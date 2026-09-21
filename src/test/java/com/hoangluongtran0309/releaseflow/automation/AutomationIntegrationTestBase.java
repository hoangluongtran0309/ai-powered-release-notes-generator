package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.ConfluenceStub;
import com.hoangluongtran0309.releaseflow.support.GitHubStub;
import com.hoangluongtran0309.releaseflow.support.NotionStub;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
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
 * What every automation test needs: the providers a delivery can reach, and an
 * Organization with a project, a published release, and its notes. The stubs are shared
 * so the tests share one application context.
 */
public abstract class AutomationIntegrationTestBase extends PostgreSqlIntegrationTest {

    protected static final GitHubStub GITHUB = GitHubStub.start();
    protected static final SlackStub SLACK = SlackStub.start();
    protected static final SmtpStub SMTP = SmtpStub.start();
    protected static final NotionStub NOTION = NotionStub.start();
    protected static final ConfluenceStub CONFLUENCE = ConfluenceStub.start();
    protected static final String GITHUB_TOKEN = "github_pat_automation-test";
    protected static final String SENDER = "releases@example.com";
    /** A made-up Notion page id: the shape the validator wants, addressing nothing. */
    protected static final String NOTION_PARENT_PAGE = "1a2b3c4d5e6f4a5b8c9d0e1f2a3b4c5d";
    protected static final String CONFLUENCE_ACCOUNT = "releases@example.com";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected RegistrationService registrationService;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    private AutomationWorker automationWorker;

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
        registry.add("releaseflow.automation.notion.api-base-url", NOTION::baseUrl);
        // A Confluence action still names a real atlassian.net site; only where the call
        // goes is the deployment's business.
        registry.add("releaseflow.automation.confluence.api-base-url", CONFLUENCE::baseUrl);
    }

    @AfterAll
    static void stopStubs() {
        GITHUB.reset();
        SLACK.reset();
        SMTP.reset();
        NOTION.reset();
        CONFLUENCE.reset();
    }

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        GITHUB.reset();
        SLACK.reset();
        SMTP.reset();
        NOTION.reset();
        CONFLUENCE.reset();
        // Published releases reject DELETE by design; TRUNCATE bypasses row triggers.
        jdbcTemplate.execute("TRUNCATE public_changelog_entries, automation_action_runs, automation_runs, automation_publish_jobs, automation_rule_actions, automation_rules,"
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

    /** Works everything the worker has waiting, deliveries and outbox rows alike. */
    protected void deliverEverything() {
        while (automationWorker.processOne()) {
            // Keep going until nothing is left.
        }
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
        return createProject(owner, "ReleaseFlow");
    }

    protected UUID createProject(Owner owner, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name)))
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
        return slackRule(name, trigger, projectId, audienceId, language, "");
    }

    /** The same, plus whatever the trigger itself needs, as further JSON fields. */
    protected String slackRule(
            String name,
            String trigger,
            UUID projectId,
            UUID audienceId,
            String language,
            String triggerFields
    ) {
        return """
                {"name":"%s","triggerType":"%s","projectId":%s,%s
                 "actions":[{"actionType":"SLACK","audienceId":"%s","language":"%s","secret":"%s"}]}
                """.formatted(
                name,
                trigger,
                projectId == null ? "null" : "\"" + projectId + "\"",
                triggerFields,
                audienceId,
                language,
                slackWebhook()
        );
    }

    /** A schedule that repeats one published release. */
    protected String cronRule(
            String name,
            UUID projectId,
            UUID audienceId,
            UUID releaseId,
            String expression,
            String timeZone
    ) {
        return slackRule(name, "SCHEDULED_CRON", projectId, audienceId, "en",
                """
                 "releaseId":"%s","cronExpression":"%s","cronTimeZone":"%s","""
                        .formatted(releaseId, expression, timeZone));
    }

    protected String reminderRule(String name, UUID projectId, UUID audienceId, int daysBefore) {
        return slackRule(name, "UPCOMING_RELEASE_REMINDER", projectId, audienceId, "en",
                "\n \"daysBefore\":%d,".formatted(daysBefore));
    }

    protected String webhookRule(String name, UUID projectId, UUID audienceId) {
        return slackRule(name, "EXTERNAL_WEBHOOK", projectId, audienceId, "en", "");
    }

    /** A rule body with one Notion action for an audience, in English. */
    protected String notionRule(String name, String trigger, UUID projectId, UUID audienceId) {
        return notionRule(name, trigger, projectId, audienceId, NOTION_PARENT_PAGE, "notion-integration-token");
    }

    protected String notionRule(
            String name,
            String trigger,
            UUID projectId,
            UUID audienceId,
            String parentPageId,
            String secret
    ) {
        return """
                {"name":"%s","triggerType":"%s","projectId":%s,
                 "actions":[{"actionType":"NOTION","audienceId":"%s","language":"en",
                 "parentPageId":%s,"secret":%s}]}
                """.formatted(
                name,
                trigger,
                projectId == null ? "null" : "\"" + projectId + "\"",
                audienceId,
                json(parentPageId),
                json(secret)
        );
    }

    /** A rule body with one Confluence action for an audience, in English. */
    protected String confluenceRule(String name, String trigger, UUID projectId, UUID audienceId) {
        return confluenceRule(
                name, trigger, projectId, audienceId, ConfluenceStub.SITE, "42", "confluence-api-token");
    }

    protected String confluenceRule(
            String name,
            String trigger,
            UUID projectId,
            UUID audienceId,
            String siteUrl,
            String spaceId,
            String secret
    ) {
        return """
                {"name":"%s","triggerType":"%s","projectId":%s,
                 "actions":[{"actionType":"CONFLUENCE","audienceId":"%s","language":"en",
                 "siteUrl":%s,"email":"%s","spaceId":%s,"secret":%s}]}
                """.formatted(
                name,
                trigger,
                projectId == null ? "null" : "\"" + projectId + "\"",
                audienceId,
                json(siteUrl),
                CONFLUENCE_ACCOUNT,
                json(spaceId),
                json(secret)
        );
    }

    private static String json(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    /** Makes a schedule due, the way time passing would. */
    protected void setNextFireAt(UUID ruleId, Instant nextFireAt) {
        jdbcTemplate.update(
                "UPDATE automation_rules SET next_fire_at = ? WHERE id = ?", Timestamp.from(nextFireAt), ruleId);
    }

    protected Instant nextFireAt(UUID ruleId) {
        Timestamp booked = jdbcTemplate.queryForObject(
                "SELECT next_fire_at FROM automation_rules WHERE id = ?", Timestamp.class, ruleId);
        return booked == null ? null : booked.toInstant();
    }

    /** Plans an approved release, which is what a reminder rule watches for. */
    protected void schedule(Owner owner, UUID projectId, UUID releaseId, Instant plannedReleaseAt) throws Exception {
        mockMvc.perform(put("/api/projects/{projectId}/releases/{releaseId}/schedule", projectId, releaseId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedReleaseAt\":\"%s\"}".formatted(plannedReleaseAt.toString())))
                .andExpect(status().isOk());
    }

    /** Signs a call exactly as a caller of an automation webhook must. */
    protected static String sign(
            String secret,
            String timestamp,
            String deliveryId,
            String method,
            String path,
            String body
    ) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        String canonical = String.join(
                "\n",
                timestamp,
                deliveryId,
                method,
                path,
                HexFormat.of().formatHex(sha256.digest(body.getBytes(StandardCharsets.UTF_8)))
        );
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    protected ResultActions enable(Owner owner, UUID ruleId) throws Exception {
        return mockMvc.perform(post("/api/automation/rules/{ruleId}/enable", ruleId)
                .session(owner.session())
                .with(csrf()));
    }

    protected record Owner(UUID organizationId, UUID userId, MockHttpSession session) {
    }
}
