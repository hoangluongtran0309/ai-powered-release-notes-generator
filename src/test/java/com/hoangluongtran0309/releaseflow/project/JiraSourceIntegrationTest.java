package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.JiraStub;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Connecting a Jira project, which is read on a schedule and never delivers anything. */
class JiraSourceIntegrationTest extends PostgreSqlIntegrationTest {

    private static final JiraStub JIRA = JiraStub.start();
    private static final String EMAIL = "release-bot@acme.test";
    private static final String TOKEN = "atlassian-setup-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void jiraProperties(DynamicPropertyRegistry registry) {
        registry.add("releaseflow.jira.api-base-url", JIRA::baseUrl);
        registry.add("releaseflow.jira.timeout", () -> "PT2S");
    }

    @AfterAll
    static void stopStub() {
        JIRA.close();
    }

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        JIRA.reset();
        jdbcTemplate.update("DELETE FROM source_sync_jobs");
        jdbcTemplate.update("DELETE FROM integration_sources");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void connectsAProjectJiraConfirmsWithNoWebhookAndAFirstPollLater() throws Exception {
        Owner owner = signIn("owner@example.com");
        Instant before = Instant.now();

        connect(owner, JiraStub.SITE + "/", "APP", EMAIL, TOKEN)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("JIRA"))
                .andExpect(jsonPath("$.externalProjectKey").value("APP"))
                .andExpect(jsonPath("$.apiBaseUrl").value(JiraStub.SITE))
                .andExpect(jsonPath("$.credentialIdentity").value(EMAIL))
                .andExpect(jsonPath("$.webhookAuthMode").value("NONE"))
                .andExpect(jsonPath("$.webhookId").doesNotExist())
                .andExpect(jsonPath("$.webhookPath").doesNotExist())
                .andExpect(jsonPath("$.webhookSecret").doesNotExist())
                .andExpect(content().string(not(containsString(TOKEN))));

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT webhook_id, secret_nonce, secret_ciphertext, token_ciphertext, created_at, poll_cursor_at,
                       next_poll_at
                FROM integration_sources""");
        assertThat(row.get("webhook_id")).isNull();
        assertThat(row.get("secret_ciphertext")).isNull();
        assertThat(row.get("token_ciphertext")).isNotNull();
        // Nothing before the connection is read, and the first read waits one interval.
        assertThat(row.get("poll_cursor_at")).isEqualTo(row.get("created_at"));
        Instant nextPoll = ((Timestamp) row.get("next_poll_at")).toInstant();
        assertThat(nextPoll).isCloseTo(before.plus(Duration.ofMinutes(5)), within(Duration.ofSeconds(30)));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM source_sync_jobs", Long.class)).isZero();

        mockMvc.perform(get("/api/projects/{projectId}/sources", owner.projectId()).session(owner.session()))
                .andExpect(jsonPath("$[0].type").value("JIRA"))
                .andExpect(jsonPath("$[0].deliveryMechanism").value("POLLING"))
                .andExpect(jsonPath("$[0].accessTokenConfigured").value(true))
                .andExpect(jsonPath("$[0].nextPollAt").exists())
                .andExpect(content().string(not(containsString(TOKEN))));
        assertThat(JIRA.requests()).singleElement()
                .satisfies(request -> assertThat(request.path()).isEqualTo("/rest/api/3/project/APP"));
    }

    @Test
    void storesNothingWhenJiraRefusesOrCannotBeReached() throws Exception {
        Owner owner = signIn("refused@example.com");

        JIRA.respondToProjectWith(401);
        connect(owner, JiraStub.SITE, "APP", EMAIL, TOKEN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("jira_token_rejected"));
        JIRA.respondToProjectWith(503);
        connect(owner, JiraStub.SITE, "APP", EMAIL, TOKEN)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("jira_unavailable"));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM integration_sources", Long.class)).isZero();
    }

    @Test
    void acceptsOnlyAnAtlassianCloudSite() throws Exception {
        Owner owner = signIn("site@example.com");

        for (String site : new String[]{
                "https://jira.example.com", "https://acme.atlassian.net:8443", "http://acme.atlassian.net",
                "https://acme.atlassian.net.evil.test", "https://bot:pw@acme.atlassian.net"}) {
            connect(owner, site, "APP", EMAIL, TOKEN)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("jira_site_invalid"));
        }

        assertThat(JIRA.requests()).as("no other address is ever called").isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM integration_sources", Long.class)).isZero();
    }

    @Test
    void needsASiteAProjectKeyAnAccountAndAToken() throws Exception {
        Owner owner = signIn("missing@example.com");

        mockMvc.perform(post("/api/projects/{projectId}/sources", owner.projectId())
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"JIRA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors.siteUrl").exists())
                .andExpect(jsonPath("$.errors.projectKey").exists())
                .andExpect(jsonPath("$.errors.accountEmail").exists())
                .andExpect(jsonPath("$.errors.apiToken").exists());
        connect(owner, JiraStub.SITE, "app-1", EMAIL, TOKEN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.projectKey").exists());

        assertThat(JIRA.requests()).as("nothing is asked before the request is complete").isEmpty();
    }

    @Test
    void connectsAProjectOnce() throws Exception {
        Owner owner = signIn("once@example.com");
        connect(owner, JiraStub.SITE, "APP", EMAIL, TOKEN).andExpect(status().isCreated());

        connect(owner, JiraStub.SITE, "APP", EMAIL, TOKEN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("jira_project_already_connected"));
    }

    @Test
    void replacesTheTokenOnlyWhenJiraAcceptsIt() throws Exception {
        Owner owner = signIn("rotate@example.com");
        UUID sourceId = connectedSourceId(owner);

        JIRA.respondToProjectWith(403);
        setToken(owner, owner.projectId(), sourceId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("jira_token_rejected"));

        JIRA.respondToProjectWith(200);
        setToken(owner, owner.projectId(), sourceId).andExpect(status().isNoContent());
        // The stored account signs every call; only the token changed.
        assertThat(JIRA.requests().getLast().authorization()).isEqualTo("Basic " + java.util.Base64.getEncoder()
                .encodeToString((EMAIL + ":new-token").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void offersNoManualImportForAPolledProject() throws Exception {
        Owner owner = signIn("import@example.com");
        UUID sourceId = connectedSourceId(owner);

        mockMvc.perform(post("/api/projects/{projectId}/sources/{sourceId}/imports", owner.projectId(), sourceId)
                        .session(owner.session()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("source_import_not_supported"));
        mockMvc.perform(get("/api/projects/{projectId}/imports", owner.projectId()).session(owner.session()))
                .andExpect(jsonPath("$[0].supportsImport").value(false))
                .andExpect(jsonPath("$[0].polled").value(true))
                .andExpect(jsonPath("$[0].nextPollAt").exists());
        mockMvc.perform(get("/changes").param("project", owner.projectId().toString()).session(owner.session()))
                .andExpect(content().string(containsString("Polled")))
                .andExpect(content().string(containsString("not read yet")))
                .andExpect(content().string(not(containsString("Import last 90 days"))));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM source_sync_jobs", Long.class)).isZero();
    }

    @Test
    void theProjectsPageConnectsAProjectAndShowsNoWebhook() throws Exception {
        Owner owner = signIn("page@example.com");

        mockMvc.perform(post("/projects/{projectId}/sources", owner.projectId())
                        .session(owner.session()).with(csrf())
                        .param("type", "JIRA")
                        .param("siteUrl", JiraStub.SITE)
                        .param("projectKey", "APP")
                        .param("accountEmail", EMAIL)
                        .param("apiToken", TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("source-created"))
                .andExpect(content().string(containsString("Jira connected")))
                .andExpect(content().string(containsString("nothing to paste anywhere")))
                .andExpect(content().string(not(containsString("Webhook path"))))
                .andExpect(content().string(not(containsString("Webhook secret"))))
                .andExpect(content().string(not(containsString(TOKEN))));

        mockMvc.perform(get("/projects").session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(JiraStub.SITE + " as " + EMAIL)))
                .andExpect(content().string(containsString("Next read due")));
    }

    @Test
    void theProjectsPageExplainsARefusedSiteOrToken() throws Exception {
        Owner owner = signIn("page-errors@example.com");

        mockMvc.perform(post("/projects/{projectId}/sources", owner.projectId())
                        .session(owner.session()).with(csrf())
                        .param("type", "JIRA")
                        .param("siteUrl", "https://jira.example.com")
                        .param("projectKey", "APP")
                        .param("accountEmail", EMAIL)
                        .param("apiToken", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("projects"))
                .andExpect(content().string(containsString("atlassian.net address")));

        JIRA.respondToProjectWith(401);
        mockMvc.perform(post("/projects/{projectId}/sources", owner.projectId())
                        .session(owner.session()).with(csrf())
                        .param("type", "JIRA")
                        .param("siteUrl", JiraStub.SITE)
                        .param("projectKey", "APP")
                        .param("accountEmail", EMAIL)
                        .param("apiToken", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("projects"))
                .andExpect(content().string(containsString("Jira did not accept this account and token")))
                .andExpect(content().string(not(containsString(TOKEN))));
    }

    @Test
    void keepsAProjectInsideItsOrganization() throws Exception {
        Owner owner = signIn("tenant@example.com");
        UUID sourceId = connectedSourceId(owner);
        Owner other = signIn("intruder@example.com");
        JIRA.reset();

        setToken(other, other.projectId(), sourceId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("source_not_found"));
        mockMvc.perform(get("/api/projects/{projectId}/sources", owner.projectId()).session(other.session()))
                .andExpect(status().isNotFound());
        assertThat(JIRA.requests()).isEmpty();
    }

    @Test
    void theDatabaseKeepsWebhooksAndSchedulesToTheirOwnTypes() throws Exception {
        Owner owner = signIn("constraints@example.com");
        UUID organizationId = owner.organizationId();
        UUID projectId = owner.projectId();

        assertThatCode(() -> insertJira(organizationId, projectId, "OK", EMAIL, true))
                .doesNotThrowAnyException();
        // A Jira source signs in as an account and has a schedule.
        assertThatThrownBy(() -> insertJira(organizationId, projectId, "NOACCOUNT", null, true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertJira(organizationId, projectId, "NOSCHEDULE", EMAIL, false))
                .isInstanceOf(DataIntegrityViolationException.class);
        // A webhook source keeps its address and secret, and never gets a schedule or an account.
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO integration_sources
                    (id, organization_id, project_id, source_type, external_project_key, repository_owner,
                     repository_name, webhook_auth_mode, created_at)
                VALUES (?, ?, ?, 'GITHUB', 'acme/app', 'acme', 'app', 'GITHUB_HMAC', now())
                """, UUID.randomUUID(), organizationId, projectId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO integration_sources
                    (id, organization_id, project_id, source_type, external_project_key, repository_owner,
                     repository_name, webhook_auth_mode, webhook_id, secret_nonce, secret_ciphertext,
                     next_poll_at, poll_cursor_at, created_at)
                VALUES (?, ?, ?, 'GITHUB', 'acme/two', 'acme', 'two', 'GITHUB_HMAC', ?, ?, ?, now(), now(), now())
                """, UUID.randomUUID(), organizationId, projectId, UUID.randomUUID(), new byte[12], new byte[32]))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO integration_sources
                    (id, organization_id, project_id, source_type, external_project_key, repository_owner,
                     repository_name, webhook_auth_mode, webhook_id, secret_nonce, secret_ciphertext,
                     credential_identity, created_at)
                VALUES (?, ?, ?, 'GITHUB', 'acme/three', 'acme', 'three', 'GITHUB_HMAC', ?, ?, ?, 'x@y', now())
                """, UUID.randomUUID(), organizationId, projectId, UUID.randomUUID(), new byte[12], new byte[32]))
                .isInstanceOf(DataIntegrityViolationException.class);
        // A Jira source has no address to be delivered to.
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO integration_sources
                    (id, organization_id, project_id, source_type, external_project_key, api_base_url,
                     credential_identity, webhook_auth_mode, webhook_id, next_poll_at, poll_cursor_at, created_at)
                VALUES (?, ?, ?, 'JIRA', 'HOOK', ?, ?, 'NONE', ?, now(), now(), now())
                """, UUID.randomUUID(), organizationId, projectId, JiraStub.SITE, EMAIL, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseLetsOnlyAPollGoWithoutARequester() throws Exception {
        Owner owner = signIn("jobs@example.com");
        UUID sourceId = connectedSourceId(owner);

        assertThatCode(() -> insertJob(owner, sourceId, "JIRA_POLL", null, null)).doesNotThrowAnyException();
        jdbcTemplate.update("UPDATE source_sync_jobs SET status = 'COMPLETED', completed_at = now()");
        assertThatThrownBy(() -> insertJob(owner, sourceId, "HISTORICAL_IMPORT", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertJob(owner, sourceId, "JIRA_POLL", owner.userId(), "Owner"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // A provider's page token is longer than a page number.
        assertThatCode(() -> jdbcTemplate.update("UPDATE source_sync_jobs SET provider_cursor = ?",
                "t".repeat(450) + ":3")).doesNotThrowAnyException();
    }

    private void insertJira(UUID organizationId, UUID projectId, String key, String account, boolean scheduled) {
        jdbcTemplate.update("""
                        INSERT INTO integration_sources
                            (id, organization_id, project_id, source_type, external_project_key, api_base_url,
                             credential_identity, webhook_auth_mode, next_poll_at, poll_cursor_at, created_at)
                        VALUES (?, ?, ?, 'JIRA', ?, ?, ?, 'NONE', ?, ?, now())
                        """,
                UUID.randomUUID(), organizationId, projectId, key, JiraStub.SITE, account,
                scheduled ? Timestamp.from(Instant.now()) : null,
                scheduled ? Timestamp.from(Instant.now()) : null);
    }

    private void insertJob(Owner owner, UUID sourceId, String type, UUID requestedBy, String requesterName) {
        jdbcTemplate.update("""
                        INSERT INTO source_sync_jobs
                            (id, organization_id, project_id, source_id, job_type, status, window_start, window_end,
                             provider_cursor, scanned_count, imported_count, item_limit, attempts, requested_by,
                             requester_name, next_attempt_at, created_at)
                        VALUES (?, ?, ?, ?, ?, 'PENDING', now() - interval '1 hour', now(), ':0', 0, 0, 100, 0, ?, ?,
                                now(), now())
                        """,
                UUID.randomUUID(), owner.organizationId(), owner.projectId(), sourceId, type, requestedBy,
                requesterName);
    }

    private ResultActions connect(Owner owner, String site, String projectKey, String email, String token)
            throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/sources", owner.projectId())
                .session(owner.session()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type":"JIRA","siteUrl":"%s","projectKey":"%s","accountEmail":"%s","apiToken":"%s"}"""
                        .formatted(site, projectKey, email, token)));
    }

    private UUID connectedSourceId(Owner owner) throws Exception {
        MvcResult created = connect(owner, JiraStub.SITE, "APP", EMAIL, TOKEN)
                .andExpect(status().isCreated())
                .andReturn();
        JIRA.reset();
        return UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
    }

    private ResultActions setToken(Owner owner, UUID projectId, UUID sourceId) throws Exception {
        return mockMvc.perform(put("/api/projects/{projectId}/sources/{sourceId}/token", projectId, sourceId)
                .session(owner.session()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"new-token\"}"));
    }

    private Owner signIn(String email) throws Exception {
        RegistrationRequest registration = new RegistrationRequest();
        registration.setOrganizationName(email);
        registration.setDisplayName("Owner");
        registration.setEmail(email);
        registration.setPassword("owner-password");
        RegistrationResult registered = registrationService.register(registration);
        MvcResult login = mockMvc.perform(post("/login").with(csrf())
                        .param("email", email).param("password", "owner-password"))
                .andExpect(status().isFound())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        MvcResult project = mockMvc.perform(post("/api/projects").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ReleaseFlow\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new Owner(
                registered.organizationId(),
                registered.userId(),
                UUID.fromString(JsonPath.read(project.getResponse().getContentAsString(), "$.id")),
                session
        );
    }

    private record Owner(UUID organizationId, UUID userId, UUID projectId, MockHttpSession session) {
    }
}
