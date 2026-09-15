package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.hoangluongtran0309.releaseflow.support.TestCategories;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChangeReviewIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private ChangeRepository changeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM change_processing_jobs");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM integration_sources");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void confirmingAnUnchangedClassificationKeepsItsSourceAndRecordsTheReviewer() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner.session());
        UUID changeId = seedChange(owner, projectId, 1, "feat(api)!: drop the v1 export", List.of());

        review(owner, projectId, changeId, "feature", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("FEATURE"))
                .andExpect(jsonPath("$.breaking").value(true))
                .andExpect(jsonPath("$.needsReview").value(false))
                .andExpect(jsonPath("$.classificationSource").value("RULES"))
                .andExpect(jsonPath("$.reviewedBy").value(owner.userId().toString()))
                .andExpect(jsonPath("$.reviewerName").value("Mai Tran"))
                .andExpect(jsonPath("$.reviewedAt", notNullValue()))
                .andExpect(jsonPath("$.reasons[0]").value("Title type \"feat\""));
    }

    @Test
    void correctingAClassificationMakesTheReviewerItsSourceAndMayClearBreaking() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner.session());
        UUID breaking = seedChange(owner, projectId, 1, "fix!: rename a private helper", List.of());
        UUID unknown = seedChange(owner, projectId, 2, "Tidy the exporter", List.of());

        review(owner, projectId, breaking, "maintenance", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("MAINTENANCE"))
                .andExpect(jsonPath("$.breaking").value(false))
                .andExpect(jsonPath("$.needsReview").value(false))
                .andExpect(jsonPath("$.classificationSource").value("HUMAN"));
        review(owner, projectId, unknown, "fix", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("FIX"))
                .andExpect(jsonPath("$.classificationSource").value("HUMAN"))
                .andExpect(jsonPath("$.aiEligible").value(false));

        mockMvc.perform(post("/api/projects/{projectId}/changes/{changeId}/ai-classification", projectId, unknown)
                        .session(owner.session())
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("change_not_eligible_for_ai"));
    }

    @Test
    void anyChangeCanBeReviewedAgainAndTheLatestReviewWins() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner.session());
        UUID classified = seedChange(owner, projectId, 1, "feat: add the inbox", List.of());

        review(owner, projectId, classified, "documentation", false)
                .andExpect(jsonPath("$.classificationSource").value("HUMAN"));
        review(owner, projectId, classified, "feature", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("FEATURE"))
                .andExpect(jsonPath("$.breaking").value(true))
                .andExpect(jsonPath("$.needsReview").value(false))
                .andExpect(jsonPath("$.classificationSource").value("HUMAN"));

        assertThat(changeRepository.findById(classified).orElseThrow().getReviewedAt()).isNotNull();
    }

    @Test
    void rejectsInvalidReviewsAndOtherTenants() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        Owner other = registerAndLogin("other@example.com", "Other Owner");
        UUID projectId = createProject(owner.session());
        UUID changeId = seedChange(owner, projectId, 1, "Tidy the exporter", List.of());

        review(owner, projectId, changeId, "unknown", false)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("invalid_change_review"));
        review(owner, projectId, changeId, "security", false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_change_review"));
        mockMvc.perform(post("/api/projects/{projectId}/changes/{changeId}/review", projectId, changeId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"fix\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors.breaking").exists());

        review(other, projectId, changeId, "fix", false)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("change_not_found"));
        mockMvc.perform(post("/api/projects/{projectId}/changes/{changeId}/review", projectId, changeId)
                        .session(owner.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("fix", false)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/projects/{projectId}/changes/{changeId}/review", projectId, changeId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("fix", false)))
                .andExpect(status().isUnauthorized());

        Change unchanged = changeRepository.findById(changeId).orElseThrow();
        assertThat(unchanged.isNeedsReview()).isTrue();
        assertThat(unchanged.getReviewedAt()).isNull();
    }

    @Test
    void filtersByReviewStatus() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner.session());
        seedChange(owner, projectId, 1, "feat: add the inbox", List.of());
        UUID reviewed = seedChange(owner, projectId, 2, "fix!: rename config keys", List.of());
        seedChange(owner, projectId, 3, "Tidy the exporter", List.of());
        review(owner, projectId, reviewed, "fix", true).andExpect(status().isOk());

        expectNumbers(owner, projectId, "needs-review", 3);
        expectNumbers(owner, projectId, "classified", 1);
        expectNumbers(owner, projectId, "reviewed", 2);
    }

    @Test
    void reviewsFromTheInboxAndShowsWhoReviewed() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner.session());
        UUID classified = seedChange(owner, projectId, 1, "feat: add the inbox", List.of());
        UUID unknown = seedChange(owner, projectId, 2, "Tidy the exporter", List.of("breaking-change"));

        mockMvc.perform(get("/changes").session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(matchesPattern(
                        "(?s).*id=\"change-" + unknown + "\".*<p class=\"mb-2 text-sm font-semibold\">Review</p>.*"
                                + "<option value=\"\" disabled selected=\"selected\">Choose a category</option>.*"
                                + "name=\"breaking\" value=\"true\" class=\"checkbox checkbox-sm\" checked=\"checked\".*"
                                + "Confirm review.*id=\"change-" + classified + "\".*Edit classification.*"
                                + "<option value=\"FEATURE\" selected=\"selected\">Feature</option>.*")))
                .andExpect(content().string(not(containsString("<option value=\"unknown\" selected"))));

        // An unchecked checkbox sends no value, so it clears the breaking flag.
        mockMvc.perform(post("/projects/{projectId}/changes/{changeId}/review", projectId, unknown)
                        .session(owner.session())
                        .with(csrf())
                        .param("category", "maintenance")
                        .param("returnStatus", "needs-review"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/changes?project=" + projectId + "&status=needs-review#change-" + unknown));

        Change reviewed = changeRepository.findById(unknown).orElseThrow();
        assertThat(reviewed.getCategory()).isEqualTo(TestCategories.MAINTENANCE);
        assertThat(reviewed.isBreaking()).isFalse();
        assertThat(reviewed.isNeedsReview()).isFalse();
        assertThat(reviewed.getClassificationSource()).isEqualTo(ClassificationSource.HUMAN);

        mockMvc.perform(post("/projects/{projectId}/changes/{changeId}/review", projectId, classified)
                        .session(owner.session())
                        .with(csrf())
                        .param("category", "feature")
                        .param("breaking", "true"))
                .andExpect(status().isFound());

        mockMvc.perform(get("/changes").session(owner.session()))
                .andExpect(content().string(containsString(">Reviewed</span>")))
                .andExpect(content().string(matchesPattern("(?s).*Corrected by</span>\\s*<span class=\"font-semibold\">Mai Tran</span>.*")))
                .andExpect(content().string(not(containsString(">Needs review</span>"))));

        mockMvc.perform(post("/projects/{projectId}/changes/{changeId}/review", projectId, classified)
                        .session(owner.session())
                        .with(csrf())
                        .param("category", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("A reviewed change cannot stay Unknown.")));
    }

    private void expectNumbers(Owner owner, UUID projectId, String status, Integer... numbers) throws Exception {
        mockMvc.perform(get("/api/projects/{projectId}/changes", projectId)
                        .session(owner.session())
                        .param("status", status))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].pullRequestNumber").value(contains(numbers)));
    }

    private ResultActions review(Owner owner, UUID projectId, UUID changeId, String category, boolean breaking)
            throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/changes/{changeId}/review", projectId, changeId)
                .session(owner.session())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(category, breaking)));
    }

    private static String body(String category, boolean breaking) {
        return "{\"category\":\"%s\",\"breaking\":%s}".formatted(category, breaking);
    }

    private UUID seedChange(Owner owner, UUID projectId, int number, String title, List<String> labels) {
        MergedPullRequest pullRequest = new MergedPullRequest(
                number,
                title,
                null,
                "mai-dev",
                labels,
                "main",
                "0123456789abcdef0123456789abcdef01234567",
                Instant.parse("2026-09-01T10:00:00Z").plusSeconds(number),
                "https://github.com/acme/releaseflow/pull/" + number
        );
        UUID id = UUID.randomUUID();
        changeRepository.saveAndFlush(ProcessedChanges.processed(id, owner.organizationId(), projectId, pullRequest));
        return id;
    }

    private UUID createProject(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ReleaseFlow\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private Owner registerAndLogin(String email, String displayName) throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName(email);
        request.setDisplayName(displayName);
        request.setEmail(email);
        request.setPassword("owner-password");
        RegistrationResult registration = registrationService.register(request);
        MvcResult login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", "owner-password"))
                .andExpect(status().isFound())
                .andReturn();
        return new Owner(
                registration.organizationId(),
                registration.userId(),
                (MockHttpSession) login.getRequest().getSession(false)
        );
    }

    private record Owner(UUID organizationId, UUID userId, MockHttpSession session) {
    }
}
