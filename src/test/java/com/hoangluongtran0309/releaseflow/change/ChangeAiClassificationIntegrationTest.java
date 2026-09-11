package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.OpenAiStub;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChangeAiClassificationIntegrationTest extends PostgreSqlIntegrationTest {

    private static final OpenAiStub OPENAI = OpenAiStub.start();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private ChangeRepository changeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void openAiProperties(DynamicPropertyRegistry registry) {
        registry.add("releaseflow.openai.base-url", OPENAI::baseUrl);
        registry.add("releaseflow.openai.api-key", () -> "sk-integration-test");
        registry.add("releaseflow.openai.model", () -> "test-model");
        registry.add("releaseflow.openai.timeout", () -> "PT2S");
    }

    @AfterAll
    static void stopOpenAi() {
        OPENAI.close();
    }

    @BeforeEach
    @AfterEach
    void reset() {
        OPENAI.reset();
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM github_integrations");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void storesTheAiSuggestionAndKeepsTheChangeInReview() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner.session());
        UUID changeId = seedChange(owner, projectId, 1, "Tidy the exporter", List.of());
        OPENAI.respondWithClassification("fix", false, "It corrects how empty tables are exported.");

        classify(owner, projectId, changeId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("FIX"))
                .andExpect(jsonPath("$.breaking").value(false))
                .andExpect(jsonPath("$.needsReview").value(true))
                .andExpect(jsonPath("$.classificationSource").value("AI"))
                .andExpect(jsonPath("$.aiStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.aiModel").value("test-model"))
                .andExpect(jsonPath("$.aiFailure", nullValue()))
                .andExpect(jsonPath("$.aiEligible").value(false))
                .andExpect(jsonPath("$.reasons[0]").value("No category rule matched"))
                .andExpect(jsonPath("$.reasons[1]")
                        .value("AI suggestion (test-model): It corrects how empty tables are exported."));

        Change stored = changeRepository.findById(changeId).orElseThrow();
        assertThat(stored.getClassificationSource()).isEqualTo(ClassificationSource.AI);
        assertThat(stored.getAiAttemptedAt()).isNotNull();
        assertThat(OPENAI.requests()).hasSize(1);
        assertThat(OPENAI.requests().getFirst().authorization()).isEqualTo("Bearer sk-integration-test");

        classify(owner, projectId, changeId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("change_not_eligible_for_ai"));
        assertThat(OPENAI.requests()).hasSize(1);
    }

    @Test
    void recordsFailuresExplicitlyAndAllowsAHumanRetry() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner.session());
        UUID changeId = seedChange(owner, projectId, 1, "Rework storage", List.of("breaking-change"));
        OPENAI.respond(500, "{\"error\":{\"message\":\"upstream exploded\"}}");

        classify(owner, projectId, changeId)
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ai_classification_failed"))
                .andExpect(jsonPath("$.detail").value("OpenAI returned HTTP 500."));
        mockMvc.perform(get("/api/projects/{projectId}/changes", projectId).session(owner.session()))
                .andExpect(jsonPath("$[0].category").value("UNKNOWN"))
                .andExpect(jsonPath("$[0].needsReview").value(true))
                .andExpect(jsonPath("$[0].classificationSource").value("RULES"))
                .andExpect(jsonPath("$[0].aiStatus").value("FAILED"))
                .andExpect(jsonPath("$[0].aiFailure").value("OpenAI returned HTTP 500."))
                .andExpect(jsonPath("$[0].aiEligible").value(true));

        OPENAI.respondWithClassification("maintenance", false, "It restructures storage internals.");
        classify(owner, projectId, changeId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("MAINTENANCE"))
                .andExpect(jsonPath("$.breaking").value(true))
                .andExpect(jsonPath("$.needsReview").value(true))
                .andExpect(jsonPath("$.aiStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.aiFailure", nullValue()));
    }

    @Test
    void onlyUnknownChangesOfTheCurrentTenantCanBeClassified() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        Owner other = registerAndLogin("other@example.com");
        UUID projectId = createProject(owner.session());
        UUID otherProjectId = createProject(other.session());
        UUID classified = seedChange(owner, projectId, 1, "feat: add inbox", List.of());
        UUID unknown = seedChange(owner, projectId, 2, "Tidy the exporter", List.of());

        classify(owner, projectId, classified)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("change_not_eligible_for_ai"));
        classify(other, projectId, unknown)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("change_not_found"));
        classify(other, otherProjectId, unknown)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("change_not_found"));
        mockMvc.perform(post("/api/projects/{projectId}/changes/{changeId}/ai-classification", projectId, unknown)
                        .session(owner.session()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/projects/{projectId}/changes/{changeId}/ai-classification", projectId, unknown)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        assertThat(OPENAI.requests()).isEmpty();
        assertThat(changeRepository.findById(unknown).orElseThrow().getAiStatus()).isEqualTo(AiStatus.NOT_REQUESTED);
    }

    @Test
    void classifiesFromTheInboxAndShowsTheOutcome() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner.session());
        seedChange(owner, projectId, 1, "feat: add inbox", List.of());
        UUID unknown = seedChange(owner, projectId, 2, "Tidy the exporter", List.of());

        mockMvc.perform(get("/changes").session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "action=\"/projects/" + projectId + "/changes/" + unknown + "/ai-classification\"")))
                .andExpect(content().string(containsString("Classify with AI")))
                .andExpect(content().string(not(containsString("RELEASEFLOW_OPENAI_API_KEY"))));

        OPENAI.respond(503, "{}");
        mockMvc.perform(post("/projects/{projectId}/changes/{changeId}/ai-classification", projectId, unknown)
                        .session(owner.session())
                        .with(csrf())
                        .param("returnCategory", "unknown")
                        .param("returnStatus", ""))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/changes?project=" + projectId + "&category=unknown#change-" + unknown));
        mockMvc.perform(get("/changes").session(owner.session()).param("category", "unknown"))
                .andExpect(content().string(containsString("AI classification failed:")))
                .andExpect(content().string(containsString("OpenAI returned HTTP 503.")))
                .andExpect(content().string(containsString("Retry with AI")));

        OPENAI.respondWithClassification("documentation", false, "It only edits the README.");
        mockMvc.perform(post("/projects/{projectId}/changes/{changeId}/ai-classification", projectId, unknown)
                        .session(owner.session())
                        .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/changes?project=" + projectId + "#change-" + unknown));
        mockMvc.perform(get("/changes").session(owner.session()))
                .andExpect(content().string(containsString("AI suggestion")))
                .andExpect(content().string(containsString("title=\"Suggested by test-model\"")))
                .andExpect(content().string(containsString("AI suggestion (test-model): It only edits the README.")))
                .andExpect(content().string(not(containsString("Classify with AI"))))
                .andExpect(content().string(not(containsString("AI classification failed:"))));

        mockMvc.perform(post("/projects/{projectId}/changes/{changeId}/ai-classification", projectId, UUID.randomUUID())
                        .session(owner.session())
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Change was not found.")));
    }

    private ResultActions classify(Owner owner, UUID projectId, UUID changeId) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/changes/{changeId}/ai-classification", projectId, changeId)
                .session(owner.session())
                .with(csrf()));
    }

    private UUID seedChange(Owner owner, UUID projectId, int number, String title, List<String> labels) {
        MergedPullRequest pullRequest = new MergedPullRequest(
                number,
                title,
                "Description of #" + number,
                "mai-dev",
                labels,
                "main",
                "0123456789abcdef0123456789abcdef01234567",
                Instant.parse("2026-09-01T10:00:00Z").plusSeconds(number),
                "https://github.com/acme/releaseflow/pull/" + number
        );
        UUID id = UUID.randomUUID();
        changeRepository.saveAndFlush(new Change(
                id,
                owner.organizationId(),
                projectId,
                pullRequest,
                ChangeClassifier.classify(pullRequest),
                UUID.randomUUID(),
                Instant.now()
        ));
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
