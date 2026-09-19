package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.GitHubStub;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.hoangluongtran0309.releaseflow.support.SlackStub;
import com.hoangluongtran0309.releaseflow.support.SmtpStub;
import com.hoangluongtran0309.releaseflow.support.TestChanges;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What every automation test needs: the three providers a delivery can reach, and an
 * Organization with a project, a published release, and its notes. The stubs are shared
 * so the tests share one application context.
 */
abstract class AutomationIntegrationTestBase extends PostgreSqlIntegrationTest {

    protected static final GitHubStub GITHUB = GitHubStub.start();
    protected static final SlackStub SLACK = SlackStub.start();
    protected static final SmtpStub SMTP = SmtpStub.start();
    protected static final String GITHUB_TOKEN = "github_pat_automation-test";
    protected static final String SENDER = "releases@example.com";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected RegistrationService registrationService;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void automationProperties(DynamicPropertyRegistry registry) {
        registry.add("releaseflow.github.api-base-url", GITHUB::baseUrl);
        registry.add("releaseflow.github.timeout", () -> "PT2S");
        registry.add("releaseflow.automation.timeout", () -> "PT2S");
        // The deployment's allowlist is the only thing a webhook URL is measured against,
        // so a test names its own stub rather than pretending to be Slack.
        registry.add("releaseflow.automation.slack.allowed-hosts", SLACK::baseUrl);
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", SMTP::port);
        registry.add("releaseflow.automation.email.from", () -> SENDER);
    }

    @AfterAll
    static void stopStubs() {
        GITHUB.reset();
        SLACK.reset();
        SMTP.reset();
    }

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        GITHUB.reset();
        SLACK.reset();
        SMTP.reset();
        // Published releases reject DELETE by design; TRUNCATE bypasses row triggers.
        jdbcTemplate.execute("TRUNCATE automation_action_runs, automation_runs, automation_publish_jobs,"
                + " release_audience_notes, release_change_reviews, release_notes, release_changes, releases");
        jdbcTemplate.update("DELETE FROM automation_rule_actions");
        jdbcTemplate.update("DELETE FROM automation_rules");
        jdbcTemplate.update("DELETE FROM change_processing_jobs");
        jdbcTemplate.update("DELETE FROM source_sync_jobs");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM integration_sources");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    protected String slackWebhook() {
        return SLACK.baseUrl() + "/services/T0/B0/secret";
    }

    protected Owner registerAndLogin(String email, String displayName) throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName(email);
        request.setDisplayName(displayName);
        request.setEmail(email);
        request.setPassword("owner-password");
        RegistrationResult registration = registrationService.register(request);
        return new Owner(registration.organizationId(), registration.userId(), login(email, "owner-password"));
    }

    protected MockHttpSession member(UUID organizationId, String email) throws Exception {
        jdbcTemplate.update(
                """
                        INSERT INTO app_users (id, organization_id, email, password_hash, display_name, role, created_at)
                        VALUES (?, ?, ?, ?, 'Member', 'MEMBER', now())
                        """,
                UUID.randomUUID(), organizationId, email, passwordEncoder.encode("member-password")
        );
        return login(email, "member-password");
    }

    protected MockHttpSession login(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/login").with(csrf()).param("email", email).param("password", password))
                .andExpect(status().isFound())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    protected UUID createProject(Owner owner) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ReleaseFlow\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    /** A GitHub source with an access token, which is what a GitHub Release action uses. */
    protected UUID connectGitHubSource(Owner owner, UUID projectId, String repository) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects/{projectId}/sources", projectId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GITHUB\",\"owner\":\"acme\",\"repository\":\"%s\"}"
                                .formatted(repository)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID sourceId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
        mockMvc.perform(put("/api/projects/{projectId}/sources/{sourceId}/token", projectId, sourceId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(GITHUB_TOKEN)))
                .andExpect(status().isNoContent());
        return sourceId;
    }

    protected UUID audienceId(Owner owner, String code) throws Exception {
        String body = mockMvc.perform(get("/api/audiences").session(owner.session()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> audiences = JsonPath.read(body, "$[?(@.code == '" + code + "')]");
        return UUID.fromString(audiences.getFirst().get("id").toString());
    }

    /** A project with one change, released as {@code version} and published. */
    protected UUID publishedRelease(Owner owner, UUID projectId, String version) throws Exception {
        UUID releaseId = approvedReleaseWithChange(owner, projectId, version);
        mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/publish", projectId, releaseId)
                        .session(owner.session())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        return releaseId;
    }

    private int releaseCounter = 1;

    /** An approved release holding one change, which a release needs before it can exist. */
    protected UUID approvedReleaseWithChange(Owner owner, UUID projectId, String version) throws Exception {
        TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, releaseCounter++,
                "feat(ui): add the inbox", "FEATURE", false, false, null);
        return approvedRelease(owner, projectId, version);
    }

    protected UUID approvedRelease(Owner owner, UUID projectId, String version) throws Exception {
        MvcResult draft = mockMvc.perform(post("/api/projects/{projectId}/releases", projectId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"%s\",\"summary\":\"Exports and clearer config.\"}".formatted(version)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID releaseId = UUID.fromString(JsonPath.read(draft.getResponse().getContentAsString(), "$.id"));
        mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/changes", projectId, releaseId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allAvailable\":true}"))
                .andExpect(status().isOk());
        String inReview = mockMvc.perform(
                        post("/api/projects/{projectId}/releases/{releaseId}/request-review", projectId, releaseId)
                                .session(owner.session())
                                .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> changes = JsonPath.read(inReview, "$.changes");
        for (Map<String, Object> change : changes) {
            mockMvc.perform(put("/api/projects/{projectId}/releases/{releaseId}/changes/{changeId}/decision",
                            projectId, releaseId, change.get("id"))
                            .session(owner.session())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"APPROVE\",\"category\":\"%s\",\"breaking\":%s}".formatted(
                                    change.get("category").toString().toLowerCase(Locale.ROOT), change.get("breaking"))))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/approve", projectId, releaseId)
                        .session(owner.session())
                        .with(csrf()))
                .andExpect(status().isOk());
        return releaseId;
    }

    /** An audience an Organization added itself, which a released note may not cover. */
    protected UUID createAudience(Owner owner, String code, String name) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/audiences")
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","displayName":"%s",
                                 "communicationIntent":"Plain language for the people who read it.",
                                 "templateBody":"- **{{whatChanged}}**\\n"}
                                """.formatted(code, name)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
    }

    protected ResultActions createRule(Owner owner, String body) throws Exception {
        return mockMvc.perform(post("/api/automation/rules")
                .session(owner.session())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    protected UUID createdRuleId(ResultActions result) throws Exception {
        return UUID.fromString(JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id"));
    }

    /** A rule body with one Slack action for an audience, in the given language. */
    protected String slackRule(String name, String trigger, UUID projectId, UUID audienceId, String language) {
        return """
                {"name":"%s","triggerType":"%s","projectId":%s,
                 "actions":[{"actionType":"SLACK","audienceId":"%s","language":"%s","secret":"%s"}]}
                """.formatted(
                name,
                trigger,
                projectId == null ? "null" : "\"" + projectId + "\"",
                audienceId,
                language,
                slackWebhook()
        );
    }

    protected ResultActions enable(Owner owner, UUID ruleId) throws Exception {
        return mockMvc.perform(post("/api/automation/rules/{ruleId}/enable", ruleId)
                .session(owner.session())
                .with(csrf()));
    }

    protected record Owner(UUID organizationId, UUID userId, MockHttpSession session) {
    }
}
