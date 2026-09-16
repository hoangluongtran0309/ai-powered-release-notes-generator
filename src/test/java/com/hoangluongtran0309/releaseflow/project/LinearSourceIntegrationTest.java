package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.LinearStub;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Connecting a Linear team, which Linear has to confirm before anything is stored. */
class LinearSourceIntegrationTest extends PostgreSqlIntegrationTest {

    private static final LinearStub LINEAR = LinearStub.start();
    private static final String TEAM = "11111111-1111-4111-8111-111111111111";
    private static final String WORKSPACE = "22222222-2222-4222-8222-222222222222";
    private static final String SECRET = "lin_wh_setup-secret";
    private static final String TOKEN = "lin_api_setup-test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void linearProperties(DynamicPropertyRegistry registry) {
        registry.add("releaseflow.linear.api-base-url", LINEAR::baseUrl);
        registry.add("releaseflow.linear.timeout", () -> "PT2S");
    }

    @AfterAll
    static void stopStub() {
        LINEAR.close();
    }

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        LINEAR.reset();
        jdbcTemplate.update("DELETE FROM source_sync_jobs");
        jdbcTemplate.update("DELETE FROM integration_sources");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void connectsATeamLinearConfirmsAndNeverEchoesTheSecret() throws Exception {
        Owner owner = signIn("owner@example.com");
        LINEAR.respondWithTeam(TEAM, WORKSPACE);

        connect(owner, TEAM, SECRET, TOKEN)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("LINEAR"))
                .andExpect(jsonPath("$.externalProjectKey").value(TEAM))
                .andExpect(jsonPath("$.externalWorkspaceKey").value(WORKSPACE))
                .andExpect(jsonPath("$.webhookAuthMode").value("LINEAR_HMAC"))
                .andExpect(jsonPath("$.webhookPath").value(containsString("/webhooks/linear/")))
                // Linear made the secret, so ReleaseFlow has none to reveal.
                .andExpect(jsonPath("$.webhookSecret").doesNotExist())
                .andExpect(content().string(not(containsString(SECRET))))
                .andExpect(content().string(not(containsString(TOKEN))));

        mockMvc.perform(get("/api/projects/{projectId}/sources", owner.projectId()).session(owner.session()))
                .andExpect(jsonPath("$[0].type").value("LINEAR"))
                .andExpect(jsonPath("$[0].externalWorkspaceKey").value(WORKSPACE))
                // The token was confirmed on the way in, so it is already set.
                .andExpect(jsonPath("$[0].accessTokenConfigured").value(true))
                .andExpect(content().string(not(containsString(SECRET))))
                .andExpect(content().string(not(containsString(TOKEN))));
        assertThat(LINEAR.requests()).hasSize(1);
        assertThat(LINEAR.requests().getFirst().authorization()).isEqualTo(TOKEN);
    }

    @Test
    void storesNothingWhenLinearRefusesTheApiKey() throws Exception {
        Owner owner = signIn("refused@example.com");
        LINEAR.respondWithNoTeam();

        connect(owner, TEAM, SECRET, TOKEN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("linear_token_rejected"));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM integration_sources", Long.class)).isZero();
    }

    @Test
    void needsATeamASecretAndAKey() throws Exception {
        Owner owner = signIn("missing@example.com");

        mockMvc.perform(post("/api/projects/{projectId}/sources", owner.projectId())
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"LINEAR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors.teamId").exists())
                .andExpect(jsonPath("$.errors.webhookSecret").exists())
                .andExpect(jsonPath("$.errors.apiToken").exists());

        assertThat(LINEAR.requests()).as("nothing is asked before the request is complete").isEmpty();
    }

    @Test
    void connectsATeamOnceAndAGitHubRepositorySeparately() throws Exception {
        Owner owner = signIn("once@example.com");
        LINEAR.respondWithTeam(TEAM, WORKSPACE);
        connect(owner, TEAM, SECRET, TOKEN).andExpect(status().isCreated());

        connect(owner, TEAM, SECRET, TOKEN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("linear_team_already_connected"));
        mockMvc.perform(post("/api/projects/{projectId}/sources", owner.projectId())
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"acme\",\"repository\":\"app\"}"))
                .andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForList("SELECT source_type FROM integration_sources", String.class))
                .containsExactlyInAnyOrder("LINEAR", "GITHUB");
    }

    @Test
    void keepsTheKeyOnlyWhileItStillReachesTheSameWorkspace() throws Exception {
        Owner owner = signIn("rotate@example.com");
        LINEAR.respondWithTeam(TEAM, WORKSPACE);
        UUID sourceId = connectedSourceId(owner);

        // A key for another workspace is not a key for this source.
        LINEAR.respondWithTeam(TEAM, "99999999-9999-4999-8999-999999999999");
        setToken(owner, sourceId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("linear_token_rejected"));

        LINEAR.respondWithTeam(TEAM, WORKSPACE);
        setToken(owner, sourceId).andExpect(status().isNoContent());
    }

    @Test
    void offersNoHistoryImportForATeam() throws Exception {
        Owner owner = signIn("import@example.com");
        LINEAR.respondWithTeam(TEAM, WORKSPACE);
        UUID sourceId = connectedSourceId(owner);

        mockMvc.perform(post("/api/projects/{projectId}/sources/{sourceId}/imports", owner.projectId(), sourceId)
                        .session(owner.session()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("source_import_not_supported"));
        mockMvc.perform(post("/api/projects/{projectId}/sources/{sourceId}/imports/resume", owner.projectId(), sourceId)
                        .session(owner.session()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("source_import_not_supported"));
        mockMvc.perform(get("/api/projects/{projectId}/imports", owner.projectId()).session(owner.session()))
                .andExpect(jsonPath("$[0].supportsImport").value(false));
        // The Change Inbox does not offer what the provider cannot do.
        mockMvc.perform(get("/changes").param("project", owner.projectId().toString()).session(owner.session()))
                .andExpect(content().string(not(containsString("Import last 90 days"))));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM source_sync_jobs", Long.class)).isZero();
    }

    @Test
    void keepsATeamInsideItsOrganization() throws Exception {
        Owner owner = signIn("tenant@example.com");
        LINEAR.respondWithTeam(TEAM, WORKSPACE);
        UUID sourceId = connectedSourceId(owner);
        Owner other = signIn("intruder@example.com");
        LINEAR.reset();

        mockMvc.perform(put("/api/projects/{projectId}/sources/{sourceId}/token", other.projectId(), sourceId)
                        .session(other.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(TOKEN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("source_not_found"));
        assertThat(LINEAR.requests()).isEmpty();
    }

    @Test
    void theDatabaseKeepsATeamToItsOwnColumns() throws Exception {
        Owner owner = signIn("constraints@example.com");
        UUID projectId = owner.projectId();
        UUID organizationId = owner.organizationId();

        assertThatCode(() -> insert(organizationId, projectId, "LINEAR", "team-1", null, null, null,
                WORKSPACE, "LINEAR_HMAC")).doesNotThrowAnyException();
        // A Linear source names its workspace, and nothing else does.
        assertThatThrownBy(() -> insert(organizationId, projectId, "LINEAR", "team-2", null, null, null,
                null, "LINEAR_HMAC")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(organizationId, projectId, "GITHUB", "acme/app", "acme", "app", null,
                WORKSPACE, "GITHUB_HMAC")).isInstanceOf(DataIntegrityViolationException.class);
        // A mode belongs to its provider.
        assertThatThrownBy(() -> insert(organizationId, projectId, "LINEAR", "team-3", null, null, null,
                WORKSPACE, "GITHUB_HMAC")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(organizationId, projectId, "GITHUB", "acme/two", "acme", "two", null,
                null, "LINEAR_HMAC")).isInstanceOf(DataIntegrityViolationException.class);
        // A Linear source has no repository and no instance of its own.
        assertThatThrownBy(() -> insert(organizationId, projectId, "LINEAR", "team-4", "acme", "four", null,
                WORKSPACE, "LINEAR_HMAC")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(organizationId, projectId, "LINEAR", "team-5", null, null,
                "https://linear.app", WORKSPACE, "LINEAR_HMAC")).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insert(
            UUID organizationId,
            UUID projectId,
            String sourceType,
            String externalProjectKey,
            String repositoryOwner,
            String repositoryName,
            String apiBaseUrl,
            String externalWorkspaceKey,
            String webhookAuthMode
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO integration_sources
                            (id, organization_id, project_id, source_type, external_project_key, repository_owner,
                             repository_name, api_base_url, external_workspace_key, webhook_auth_mode, webhook_id,
                             secret_nonce, secret_ciphertext, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                        """,
                UUID.randomUUID(), organizationId, projectId, sourceType, externalProjectKey, repositoryOwner,
                repositoryName, apiBaseUrl, externalWorkspaceKey, webhookAuthMode, UUID.randomUUID(),
                new byte[12], new byte[32]
        );
    }

    private ResultActions connect(Owner owner, String teamId, String secret, String token) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/sources", owner.projectId())
                .session(owner.session()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type":"LINEAR","teamId":"%s","webhookSecret":"%s","apiToken":"%s"}"""
                        .formatted(teamId, secret, token)));
    }

    private UUID connectedSourceId(Owner owner) throws Exception {
        MvcResult created = connect(owner, TEAM, SECRET, TOKEN).andExpect(status().isCreated()).andReturn();
        LINEAR.reset();
        return UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
    }

    private ResultActions setToken(Owner owner, UUID sourceId) throws Exception {
        return mockMvc.perform(put("/api/projects/{projectId}/sources/{sourceId}/token", owner.projectId(), sourceId)
                .session(owner.session()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(TOKEN)));
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
                UUID.fromString(JsonPath.read(project.getResponse().getContentAsString(), "$.id")),
                session
        );
    }

    private record Owner(UUID organizationId, UUID projectId, MockHttpSession session) {
    }
}
