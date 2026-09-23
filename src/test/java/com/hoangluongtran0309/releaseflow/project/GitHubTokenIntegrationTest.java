package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.GitHubStub;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GitHubTokenIntegrationTest extends PostgreSqlIntegrationTest {

    private static final GitHubStub GITHUB = GitHubStub.start();
    private static final String TOKEN = "github_pat_11ABCDEF0123456789_secretvalue";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private IntegrationSourceRepository integrationRepository;

    @Autowired
    private SourceAccess repositoryAccess;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void gitHubProperties(DynamicPropertyRegistry registry) {
        registry.add("releaseflow.github.api-base-url", GITHUB::baseUrl);
        registry.add("releaseflow.github.timeout", () -> "PT2S");
    }

    @AfterAll
    static void stopStub() {
        GITHUB.close();
    }

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        GITHUB.reset();
        jdbcTemplate.update("DELETE FROM integration_sources");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void storesAVerifiedTokenEncryptedAndNeverReturnsIt() throws Exception {
        Owner owner = connectedOwner("owner@example.com");

        mockMvc.perform(putToken(owner, TOKEN))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", containsString("no-store")));

        assertThat(GITHUB.requests()).singleElement().satisfies(request -> {
            assertThat(request.path()).isEqualTo("/repos/acme/releaseflow/pulls");
            assertThat(request.authorization()).isEqualTo("Bearer " + TOKEN);
        });
        IntegrationSource integration = integrationRepository.findAll().getFirst();
        assertThat(integration.hasAccessToken()).isTrue();
        assertThat(new String(integration.getToken().ciphertext())).doesNotContain(TOKEN);
        assertThat(repositoryAccess.find(owner.organizationId(), owner.projectId(), owner.sourceId()))
                .hasValueSatisfying(credentials -> {
                    assertThat(credentials.accessToken()).contains(TOKEN);
                    assertThat(credentials.toString()).doesNotContain(TOKEN);
                });
        assertThat(repositoryAccess.find(UUID.randomUUID(), owner.projectId(), owner.sourceId())).isEmpty();

        mockMvc.perform(get("/api/projects").session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sources[0].accessTokenConfigured").value(true))
                .andExpect(jsonPath("$[0].sources[0].accessTokenUpdatedAt").isNotEmpty())
                .andExpect(content().string(not(containsString(TOKEN))));
        mockMvc.perform(get("/projects").session(owner.session()))
                .andExpect(content().string(containsString("Replace access token")))
                .andExpect(content().string(not(containsString(TOKEN))));

        // Replacing the token re-encrypts it.
        byte[] firstCiphertext = integration.getToken().ciphertext();
        mockMvc.perform(putToken(owner, "github_pat_second")).andExpect(status().isNoContent());
        assertThat(integrationRepository.findAll().getFirst().getToken().ciphertext()).isNotEqualTo(firstCiphertext);
        assertThat(repositoryAccess.find(owner.organizationId(), owner.projectId(), owner.sourceId()).orElseThrow().accessToken())
                .contains("github_pat_second");
    }

    @Test
    void keepsATokenGitHubRejects() throws Exception {
        Owner owner = connectedOwner("owner@example.com");
        GITHUB.failAccessCheck(401);

        mockMvc.perform(putToken(owner, TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("github_token_rejected"))
                .andExpect(content().string(not(containsString(TOKEN))));

        assertThat(integrationRepository.findAll().getFirst().hasAccessToken()).isFalse();
    }

    @Test
    void reportsGitHubOutagesWithoutStoringTheToken() throws Exception {
        Owner owner = connectedOwner("owner@example.com");
        GITHUB.failAccessCheck(503);

        mockMvc.perform(putToken(owner, TOKEN))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("github_unavailable"));

        assertThat(integrationRepository.findAll().getFirst().hasAccessToken()).isFalse();
    }

    @Test
    void validatesTheTokenBeforeAskingGitHub() throws Exception {
        Owner owner = connectedOwner("owner@example.com");

        mockMvc.perform(putToken(owner, "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        mockMvc.perform(putToken(owner, "has spaces"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        mockMvc.perform(putToken(owner, "x".repeat(256)))
                .andExpect(status().isBadRequest());

        assertThat(GITHUB.requests()).isEmpty();
    }

    @Test
    void requiresASourceInTheCallersOrganization() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectWithoutSource = createProject(owner.session());
        Owner other = connectedOwner("other@example.com");

        mockMvc.perform(put("/api/projects/{projectId}/sources/{sourceId}/token", projectWithoutSource, other.sourceId())
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(TOKEN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("source_not_found"));
        mockMvc.perform(put("/api/projects/{projectId}/sources/{sourceId}/token", other.projectId(), other.sourceId())
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(TOKEN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("project_not_found"));

        assertThat(GITHUB.requests()).isEmpty();
    }

    @Test
    void membersCannotSetTokens() throws Exception {
        Owner owner = connectedOwner("owner@example.com");
        MockHttpSession member = memberOf(owner, "member@example.com");

        mockMvc.perform(put("/api/projects/{projectId}/sources/{sourceId}/token", owner.projectId(), owner.sourceId())
                        .session(member)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(TOKEN)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/projects/{projectId}/sources/{sourceId}/token", owner.projectId(), owner.sourceId())
                        .session(member)
                        .with(csrf())
                        .param("token", TOKEN))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/projects").session(member))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Access token")))
                .andExpect(content().string(not(containsString("Save access token"))));

        assertThat(GITHUB.requests()).isEmpty();
        assertThat(integrationRepository.findAll().getFirst().hasAccessToken()).isFalse();
    }

    @Test
    void setsTheTokenFromTheProjectsPage() throws Exception {
        Owner owner = connectedOwner("owner@example.com");
        mockMvc.perform(get("/projects").session(owner.session()))
                .andExpect(content().string(containsString("Add access token")))
                .andExpect(content().string(containsString("Every new change from this source needs review")));

        mockMvc.perform(post("/projects/{projectId}/sources/{sourceId}/token", owner.projectId(), owner.sourceId())
                        .session(owner.session())
                        .with(csrf())
                        .param("token", TOKEN))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/projects?tokenSaved"));
        mockMvc.perform(get("/projects").param("tokenSaved", "").session(owner.session()))
                .andExpect(content().string(containsString("Access token saved")))
                .andExpect(content().string(containsString("Configured")));

        GITHUB.failAccessCheck(403);
        mockMvc.perform(post("/projects/{projectId}/sources/{sourceId}/token", owner.projectId(), owner.sourceId())
                        .session(owner.session())
                        .with(csrf())
                        .param("token", "github_pat_rejected"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("GitHub did not accept this token")))
                .andExpect(content().string(not(containsString("github_pat_rejected"))));

        mockMvc.perform(post("/projects/{projectId}/sources/{sourceId}/token", owner.projectId(), owner.sourceId())
                        .session(owner.session())
                        .with(csrf())
                        .param("token", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Access token is required.")));
    }

    private MockHttpServletRequestBuilder putToken(Owner owner, String token) {
        return put("/api/projects/{projectId}/sources/{sourceId}/token", owner.projectId(), owner.sourceId())
                .session(owner.session())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(token));
    }

    private Owner connectedOwner(String email) throws Exception {
        Owner owner = registerAndLogin(email);
        UUID projectId = createProject(owner.session());
        String created = mockMvc.perform(post("/api/projects/{projectId}/sources", projectId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"acme\",\"repository\":\"releaseflow\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Owner(owner.organizationId(), projectId, UUID.fromString(JsonPath.read(created, "$.id")), owner.session());
    }

    private UUID createProject(MockHttpSession session) throws Exception {
        MvcResult project = mockMvc.perform(post("/api/projects")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ReleaseFlow\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(project.getResponse().getContentAsString(), "$.id"));
    }

    private Owner registerAndLogin(String email) throws Exception {
        RegistrationRequest registration = new RegistrationRequest();
        registration.setOrganizationName(email);
        registration.setDisplayName("Owner");
        registration.setEmail(email);
        registration.setPassword("owner-password");
        RegistrationResult registered = registrationService.register(registration);
        return new Owner(registered.organizationId(), null, null, login(email, "owner-password"));
    }

    private MockHttpSession memberOf(Owner owner, String email) throws Exception {
        jdbcTemplate.update(
                """
                        INSERT INTO app_users (id, organization_id, email, password_hash, display_name, role, created_at)
                        VALUES (?, ?, ?, ?, 'Member', 'MEMBER', now())
                        """,
                UUID.randomUUID(),
                owner.organizationId(),
                email,
                passwordEncoder.encode("member-password")
        );
        return login(email, "member-password");
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().isFound())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private record Owner(UUID organizationId, UUID projectId, UUID sourceId, MockHttpSession session) {
    }
}
