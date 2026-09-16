package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.GitLabStub;
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

import java.util.Map;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Connecting a GitLab project, and the token the provider has to accept first. */
class GitLabSourceIntegrationTest extends PostgreSqlIntegrationTest {

    private static final GitLabStub GITLAB = GitLabStub.start();
    private static final String TOKEN = "glpat-setup-test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void gitLabProperties(DynamicPropertyRegistry registry) {
        registry.add("releaseflow.gitlab.allowed-hosts", () -> "gitlab.com," + GITLAB.allowedOrigin());
        registry.add("releaseflow.gitlab.timeout", () -> "PT2S");
    }

    @AfterAll
    static void stopStub() {
        GITLAB.close();
    }

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        GITLAB.reset();
        jdbcTemplate.update("DELETE FROM integration_sources");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void connectsAGitLabProjectAndRevealsItsSecretOnce() throws Exception {
        Owner owner = signIn("owner@example.com");

        MvcResult created = connect(owner, GITLAB.baseUrl(), " Acme/Group/App ", "GITLAB_SIGNING_TOKEN")
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.type").value("GITLAB"))
                .andExpect(jsonPath("$.externalProjectKey").value("acme/group/app"))
                .andExpect(jsonPath("$.apiBaseUrl").value(GITLAB.baseUrl()))
                .andExpect(jsonPath("$.webhookAuthMode").value("GITLAB_SIGNING_TOKEN"))
                .andExpect(jsonPath("$.owner").doesNotExist())
                .andExpect(jsonPath("$.repository").doesNotExist())
                .andReturn();
        String secret = JsonPath.read(created.getResponse().getContentAsString(), "$.webhookSecret");
        String webhookPath = JsonPath.read(created.getResponse().getContentAsString(), "$.webhookPath");
        assertThat(secret).matches("[A-Za-z0-9_-]{43}");
        assertThat(webhookPath).startsWith("/webhooks/gitlab/");

        mockMvc.perform(get("/api/projects/{projectId}/sources", owner.projectId()).session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("GITLAB"))
                .andExpect(jsonPath("$[0].externalProjectKey").value("acme/group/app"))
                .andExpect(jsonPath("$[0].apiBaseUrl").value(GITLAB.baseUrl()))
                .andExpect(jsonPath("$[0].webhookPath").value(webhookPath))
                .andExpect(jsonPath("$[0].accessTokenConfigured").value(false))
                .andExpect(jsonPath("$[0].connectionStatus").value("ACTIVE"))
                .andExpect(content().string(not(containsString(secret))));
    }

    @Test
    void refusesAnInstanceOrProjectPathItCannotUse() throws Exception {
        Owner owner = signIn("owner@example.com");

        connect(owner, "https://gitlab.internal", "acme/app", "GITLAB_SIGNING_TOKEN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("gitlab_host_not_allowed"));
        connect(owner, "http://gitlab.com", "acme/app", "GITLAB_SIGNING_TOKEN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("gitlab_host_not_allowed"));
        connect(owner, "https://user:pass@gitlab.com", "acme/app", "GITLAB_SIGNING_TOKEN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("gitlab_base_url_invalid"));

        // The provider's own fields are required, and reported against themselves.
        mockMvc.perform(post("/api/projects/{projectId}/sources", owner.projectId())
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GITLAB\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors.apiBaseUrl").exists())
                .andExpect(jsonPath("$.errors.projectPath").exists());
        connect(owner, GITLAB.baseUrl(), "app", "GITLAB_SIGNING_TOKEN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.projectPath").value("Project path must look like group/project."));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM integration_sources", Long.class)).isZero();
    }

    @Test
    void connectsAProjectOnceAndAGitHubRepositoryOfTheSameNameSeparately() throws Exception {
        Owner owner = signIn("owner@example.com");
        connect(owner, GITLAB.baseUrl(), "acme/app", "GITLAB_SIGNING_TOKEN").andExpect(status().isCreated());

        connect(owner, GITLAB.baseUrl(), "acme/app", "GITLAB_SECRET_TOKEN")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("gitlab_project_already_connected"));
        // A key is unique per source type, so the same name on GitHub is another source.
        mockMvc.perform(post("/api/projects/{projectId}/sources", owner.projectId())
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"acme\",\"repository\":\"app\"}"))
                .andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForList("SELECT source_type FROM integration_sources", String.class))
                .containsExactlyInAnyOrder("GITLAB", "GITHUB");
    }

    @Test
    void storesTheTokenOnlyAfterGitLabAcceptsIt() throws Exception {
        Owner owner = signIn("owner@example.com");
        UUID sourceId = connectedSourceId(owner);

        GITLAB.failProjectCheck(401);
        setToken(owner, sourceId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("gitlab_token_rejected"));
        GITLAB.failProjectCheck(503);
        setToken(owner, sourceId)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("gitlab_unavailable"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM integration_sources WHERE token_ciphertext IS NOT NULL", Long.class)).isZero();

        GITLAB.reset();
        setToken(owner, sourceId).andExpect(status().isNoContent());

        assertThat(GITLAB.requests()).isNotEmpty();
        assertThat(GITLAB.requests().getLast().path()).isEqualTo("/api/v4/projects/acme%2Fgroup%2Fapp");
        assertThat(GITLAB.requests().getLast().privateToken()).isEqualTo(TOKEN);
        mockMvc.perform(get("/api/projects/{projectId}/sources", owner.projectId()).session(owner.session()))
                .andExpect(jsonPath("$[0].accessTokenConfigured").value(true))
                .andExpect(content().string(not(containsString(TOKEN))));
    }

    @Test
    void keepsASourceInsideItsOrganization() throws Exception {
        Owner owner = signIn("owner@example.com");
        UUID sourceId = connectedSourceId(owner);
        Owner other = signIn("other@example.com");

        mockMvc.perform(get("/api/projects/{projectId}/sources", owner.projectId()).session(other.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("project_not_found"));
        mockMvc.perform(put("/api/projects/{projectId}/sources/{sourceId}/token", other.projectId(), sourceId)
                        .session(other.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(TOKEN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("source_not_found"));
        assertThat(GITLAB.requests()).isEmpty();
    }

    @Test
    void thePagesConnectAGitLabProjectAndShowItsInstance() throws Exception {
        Owner owner = signIn("owner@example.com");

        mockMvc.perform(post("/projects/{projectId}/sources", owner.projectId())
                        .session(owner.session()).with(csrf())
                        .param("type", "GITLAB")
                        .param("apiBaseUrl", GITLAB.baseUrl())
                        .param("projectPath", "acme/group/app")
                        .param("webhookAuthMode", "GITLAB_SECRET_TOKEN"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("GitLab connected")))
                .andExpect(content().string(containsString("Merge request events")))
                .andExpect(content().string(containsString("X-Gitlab-Token")));

        mockMvc.perform(get("/projects").session(owner.session()))
                .andExpect(content().string(containsString("acme/group/app")))
                .andExpect(content().string(containsString(GITLAB.baseUrl())))
                .andExpect(content().string(containsString("read_api")));
    }

    @Test
    void thePageReportsAHostTheDeploymentDoesNotAllowWithoutWritingASource() throws Exception {
        Owner owner = signIn("owner@example.com");

        mockMvc.perform(post("/projects/{projectId}/sources", owner.projectId())
                        .session(owner.session()).with(csrf())
                        .param("type", "GITLAB")
                        .param("apiBaseUrl", "https://gitlab.internal")
                        .param("projectPath", "acme/app")
                        .param("webhookAuthMode", "GITLAB_SIGNING_TOKEN"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("does not allow the GitLab instance")));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM integration_sources", Long.class)).isZero();
    }

    @Test
    void theDatabaseKeepsEachSourceTypeToItsOwnColumns() throws Exception {
        Owner owner = signIn("owner@example.com");
        UUID projectId = owner.projectId();
        UUID organizationId = owner.organizationId();

        assertThatCode(() -> insert(organizationId, projectId, "GITLAB", "acme/app", null, null,
                GITLAB.baseUrl(), "GITLAB_SIGNING_TOKEN")).doesNotThrowAnyException();
        // A GitLab source has no repository owner or name, and a GitHub source must have both.
        assertThatThrownBy(() -> insert(organizationId, projectId, "GITLAB", "acme/two", "acme", "two",
                GITLAB.baseUrl(), "GITLAB_SIGNING_TOKEN")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(organizationId, projectId, "GITHUB", "acme/three", null, null,
                null, "GITHUB_HMAC")).isInstanceOf(DataIntegrityViolationException.class);
        // Only a GitLab source carries a base URL, and it must be an absolute address.
        assertThatThrownBy(() -> insert(organizationId, projectId, "GITHUB", "acme/four", "acme", "four",
                GITLAB.baseUrl(), "GITHUB_HMAC")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(organizationId, projectId, "GITLAB", "acme/five", null, null,
                "gitlab.com", "GITLAB_SIGNING_TOKEN")).isInstanceOf(DataIntegrityViolationException.class);
        // A mode belongs to its provider.
        assertThatThrownBy(() -> insert(organizationId, projectId, "GITLAB", "acme/six", null, null,
                GITLAB.baseUrl(), "GITHUB_HMAC")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(organizationId, projectId, "GITHUB", "acme/seven", "acme", "seven",
                null, "GITLAB_SECRET_TOKEN")).isInstanceOf(DataIntegrityViolationException.class);
        // An unknown type or mode is refused.
        assertThatThrownBy(() -> insert(organizationId, projectId, "BITBUCKET", "acme/eight", null, null,
                null, "GITHUB_HMAC")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(organizationId, projectId, "GITLAB", "  ", null, null,
                GITLAB.baseUrl(), "GITLAB_SIGNING_TOKEN")).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insert(
            UUID organizationId,
            UUID projectId,
            String sourceType,
            String externalProjectKey,
            String repositoryOwner,
            String repositoryName,
            String apiBaseUrl,
            String webhookAuthMode
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO integration_sources
                            (id, organization_id, project_id, source_type, external_project_key, repository_owner,
                             repository_name, api_base_url, webhook_auth_mode, webhook_id, secret_nonce,
                             secret_ciphertext, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                        """,
                UUID.randomUUID(), organizationId, projectId, sourceType, externalProjectKey, repositoryOwner,
                repositoryName, apiBaseUrl, webhookAuthMode, UUID.randomUUID(), new byte[12], new byte[32]
        );
    }

    private ResultActions connect(Owner owner, String apiBaseUrl, String projectPath, String authMode) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/sources", owner.projectId())
                .session(owner.session()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type":"GITLAB","apiBaseUrl":"%s","projectPath":"%s","webhookAuthMode":"%s"}"""
                        .formatted(apiBaseUrl, projectPath, authMode)));
    }

    private UUID connectedSourceId(Owner owner) throws Exception {
        MvcResult created = connect(owner, GITLAB.baseUrl(), "acme/group/app", "GITLAB_SIGNING_TOKEN")
                .andExpect(status().isCreated())
                .andReturn();
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
