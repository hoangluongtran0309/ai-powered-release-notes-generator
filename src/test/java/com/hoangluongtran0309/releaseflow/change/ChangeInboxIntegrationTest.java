package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class ChangeInboxIntegrationTest extends PostgreSqlIntegrationTest {

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
    void listsClassifiedChangesNewestFirstThroughRest() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner.session(), "ReleaseFlow");
        seedChange(owner, projectId, 1, "feat(ui): add inbox", List.of("enhancement"), "2026-09-01T10:00:00Z");
        seedChange(owner, projectId, 2, "fix!: drop legacy export", List.of(), "2026-09-03T10:00:00Z");
        seedChange(owner, projectId, 3, "Tidy things up", List.of(), "2026-09-02T10:00:00Z");

        mockMvc.perform(get("/api/projects/{projectId}/changes", projectId).session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].pullRequestNumber").value(2))
                .andExpect(jsonPath("$[0].category").value("FIX"))
                .andExpect(jsonPath("$[0].breaking").value(true))
                .andExpect(jsonPath("$[0].needsReview").value(true))
                .andExpect(jsonPath("$[0].reasons[1]").value("Title breaking marker \"!\""))
                .andExpect(jsonPath("$[1].pullRequestNumber").value(3))
                .andExpect(jsonPath("$[1].category").value("UNKNOWN"))
                .andExpect(jsonPath("$[1].needsReview").value(true))
                .andExpect(jsonPath("$[1].reasons[0]").value("No category rule matched"))
                .andExpect(jsonPath("$[2].pullRequestNumber").value(1))
                .andExpect(jsonPath("$[2].title").value("feat(ui): add inbox"))
                .andExpect(jsonPath("$[2].category").value("FEATURE"))
                .andExpect(jsonPath("$[2].needsReview").value(false))
                .andExpect(jsonPath("$[2].labels[0]").value("enhancement"))
                .andExpect(jsonPath("$[2].url").value("https://github.com/acme/releaseflow/pull/1"))
                .andExpect(jsonPath("$[2].mergedAt").value("2026-09-01T10:00:00Z"));
    }

    @Test
    void filtersByCategoryAndReviewStatus() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner.session(), "ReleaseFlow");
        seedChange(owner, projectId, 1, "feat: add inbox", List.of(), "2026-09-01T10:00:00Z");
        seedChange(owner, projectId, 2, "feat!: replace settings", List.of(), "2026-09-02T10:00:00Z");
        seedChange(owner, projectId, 3, "fix: handle empty body", List.of(), "2026-09-03T10:00:00Z");
        seedChange(owner, projectId, 4, "Tidy things up", List.of(), "2026-09-04T10:00:00Z");

        mockMvc.perform(get("/api/projects/{projectId}/changes", projectId).session(owner.session())
                        .param("category", "feature"))
                .andExpect(jsonPath("$[*].pullRequestNumber").value(org.hamcrest.Matchers.contains(2, 1)));
        mockMvc.perform(get("/api/projects/{projectId}/changes", projectId).session(owner.session())
                        .param("status", "needs-review"))
                .andExpect(jsonPath("$[*].pullRequestNumber").value(org.hamcrest.Matchers.contains(4, 2)));
        mockMvc.perform(get("/api/projects/{projectId}/changes", projectId).session(owner.session())
                        .param("status", "classified"))
                .andExpect(jsonPath("$[*].pullRequestNumber").value(org.hamcrest.Matchers.contains(3, 1)));
        mockMvc.perform(get("/api/projects/{projectId}/changes", projectId).session(owner.session())
                        .param("category", "feature")
                        .param("status", "classified"))
                .andExpect(jsonPath("$[*].pullRequestNumber").value(org.hamcrest.Matchers.contains(1)));
        mockMvc.perform(get("/api/projects/{projectId}/changes", projectId).session(owner.session())
                        .param("category", "unknown")
                        .param("status", ""))
                .andExpect(jsonPath("$[*].pullRequestNumber").value(org.hamcrest.Matchers.contains(4)));

        mockMvc.perform(get("/api/projects/{projectId}/changes", projectId).session(owner.session())
                        .param("category", "not a category!"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("invalid_change_filter"));
        mockMvc.perform(get("/api/projects/{projectId}/changes", projectId).session(owner.session())
                        .param("status", "approved"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_change_filter"));
    }

    @Test
    void isolatesInboxesBetweenOrganizations() throws Exception {
        Owner first = registerAndLogin("first@example.com");
        Owner second = registerAndLogin("second@example.com");
        UUID firstProject = createProject(first.session(), "First");
        UUID secondProject = createProject(second.session(), "Second");
        seedChange(first, firstProject, 1, "feat: first tenant secret feature", List.of(), "2026-09-01T10:00:00Z");

        mockMvc.perform(get("/api/projects/{projectId}/changes", firstProject).session(second.session()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("project_not_found"));
        mockMvc.perform(get("/api/projects/{projectId}/changes", secondProject).session(second.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/changes").session(second.session()).param("project", firstProject.toString()))
                .andExpect(status().isNotFound())
                .andExpect(view().name("changes"))
                .andExpect(model().attribute("pageError", "Project was not found."))
                .andExpect(content().string(not(containsString("first tenant secret feature"))));
        mockMvc.perform(get("/changes").session(second.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("first tenant secret feature"))));

        mockMvc.perform(get("/api/projects/{projectId}/changes", firstProject))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("authentication_required"));
    }

    @Test
    void rendersInboxWithBadgesReasonsAndFilters() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID firstProject = createProject(owner.session(), "First");
        UUID secondProject = createProject(owner.session(), "Second");
        seedChange(owner, firstProject, 1, "feat(ui): add inbox", List.of("enhancement"), "2026-09-01T10:00:00Z");
        seedChange(owner, firstProject, 2, "fix!: drop legacy export", List.of(), "2026-09-02T10:00:00Z");
        seedChange(owner, secondProject, 9, "docs: second project only", List.of(), "2026-09-03T10:00:00Z");

        mockMvc.perform(get("/changes").session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(view().name("changes"))
                .andExpect(model().attribute("selectedProjectId", firstProject))
                .andExpect(content().string(matchesPattern("(?s).*class=\"app-nav-link is-active\"[^>]*aria-current=\"page\".*Change Inbox.*")))
                .andExpect(content().string(containsString("feat(ui): add inbox")))
                .andExpect(content().string(containsString("Title type &quot;feat&quot;; Label &quot;enhancement&quot;")))
                .andExpect(content().string(matchesPattern("(?s).*id=\"change-[^\"]+\".*>Fix</span>.*>Breaking</span>.*>Needs review</span>.*")))
                .andExpect(content().string(containsString(">Classified</span>")))
                .andExpect(content().string(containsString("href=\"https://github.com/acme/releaseflow/pull/1\"")))
                .andExpect(content().string(containsString("2 changes")))
                .andExpect(content().string(not(containsString("second project only"))));

        mockMvc.perform(get("/changes").session(owner.session())
                        .param("project", firstProject.toString())
                        .param("status", "needs-review"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("fix!: drop legacy export")))
                .andExpect(content().string(not(containsString("feat(ui): add inbox"))))
                .andExpect(content().string(containsString("<option value=\"needs-review\" selected=\"selected\">")));

        mockMvc.perform(get("/changes").session(owner.session())
                        .param("project", secondProject.toString())
                        .param("category", "fix"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No changes match these filters.")));

        mockMvc.perform(get("/changes").session(owner.session()).param("category", "every-thing!"))
                .andExpect(status().isBadRequest())
                .andExpect(model().attributeExists("pageError"))
                .andExpect(content().string(containsString("Category must be a category code")));
    }

    @Test
    void rendersEmptyStates() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");

        mockMvc.perform(get("/changes").session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No projects have been created yet.")));

        createProject(owner.session(), "ReleaseFlow");
        mockMvc.perform(get("/changes").session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No merged pull requests yet.")));
    }

    private void seedChange(
            Owner owner,
            UUID projectId,
            int number,
            String title,
            List<String> labels,
            String mergedAt
    ) {
        MergedPullRequest pullRequest = new MergedPullRequest(
                number,
                title,
                "Description of #" + number,
                "mai-dev",
                labels,
                "main",
                "0123456789abcdef0123456789abcdef01234567",
                Instant.parse(mergedAt),
                "https://github.com/acme/releaseflow/pull/" + number
        );
        changeRepository.saveAndFlush(ProcessedChanges.processed(UUID.randomUUID(), owner.organizationId(), projectId, pullRequest));
    }

    private UUID createProject(MockHttpSession session, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private Owner registerAndLogin(String email) throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName(email);
        request.setDisplayName(email);
        request.setEmail(email);
        request.setPassword("owner-password");
        RegistrationResult registration = registrationService.register(request);
        MvcResult login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", "owner-password"))
                .andExpect(status().isFound())
                .andReturn();
        return new Owner(registration.organizationId(), (MockHttpSession) login.getRequest().getSession(false));
    }

    private record Owner(UUID organizationId, MockHttpSession session) {
    }
}
