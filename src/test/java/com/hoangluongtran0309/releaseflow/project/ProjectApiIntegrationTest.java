package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.account.AppUserRepository;
import com.hoangluongtran0309.releaseflow.account.OrganizationRepository;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectApiIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private GitHubIntegrationRepository integrationRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private CredentialCipher credentialCipher;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        integrationRepository.deleteAll();
        projectRepository.deleteAll();
        appUserRepository.deleteAll();
        deleteAudiences();
        organizationRepository.deleteAll();
    }

    @Test
    void createsListsAndConfiguresGitHubWithoutRevealingStoredCredentials() throws Exception {
        RegisteredOwner owner = registerAndLogin("owner@example.com", "owner-password");
        UUID projectId = createProject(owner.session(), " ReleaseFlow ");

        MvcResult configured = mockMvc.perform(post("/api/projects/{projectId}/github-integration", projectId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner":" Acme-Corp ","repository":" ReleaseFlow_App "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.owner").value("acme-corp"))
                .andExpect(jsonPath("$.repository").value("releaseflow_app"))
                .andExpect(jsonPath("$.webhookPath", matchesPattern("/webhooks/github/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.webhookSecret", matchesPattern("[A-Za-z0-9_-]{43}")))
                .andReturn();

        String body = configured.getResponse().getContentAsString();
        String webhookSecret = JsonPath.read(body, "$.webhookSecret");
        UUID integrationId = UUID.fromString(JsonPath.read(body, "$.id"));
        GitHubIntegration stored = integrationRepository
                .findByProjectIdAndOrganizationId(projectId, owner.registration().organizationId())
                .orElseThrow();
        byte[] aad = GitHubIntegrationService.additionalAuthenticatedData(
                owner.registration().organizationId(),
                projectId,
                integrationId,
                "acme-corp",
                "releaseflow_app"
        );
        assertThat(credentialCipher.decrypt(
                new CredentialCipher.EncryptedSecret(stored.getSecretNonce(), stored.getSecretCiphertext()),
                aad
        )).isEqualTo(webhookSecret);
        assertThat(Base64.getEncoder().encodeToString(stored.getSecretCiphertext()))
                .doesNotContain(webhookSecret);

        mockMvc.perform(get("/api/projects").session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(projectId.toString()))
                .andExpect(jsonPath("$[0].name").value("ReleaseFlow"))
                .andExpect(jsonPath("$[0].githubIntegration.owner").value("acme-corp"))
                .andExpect(jsonPath("$[0].githubIntegration.webhookSecret").doesNotExist())
                .andExpect(jsonPath("$[0].githubIntegration.secretNonce").doesNotExist())
                .andExpect(jsonPath("$[0].githubIntegration.secretCiphertext").doesNotExist());
    }

    @Test
    void scopesProjectsAndRepositoryUniquenessToOrganization() throws Exception {
        RegisteredOwner first = registerAndLogin("first@example.com", "first-password");
        RegisteredOwner second = registerAndLogin("second@example.com", "second-password");
        UUID firstProject = createProject(first.session(), "First");
        UUID secondProject = createProject(second.session(), "Second");

        configure(first.session(), firstProject, "acme", "releaseflow")
                .andExpect(status().isCreated());
        configure(second.session(), secondProject, "ACME", "RELEASEFLOW")
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/projects").session(first.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(firstProject.toString()));
        mockMvc.perform(post("/api/projects/{projectId}/github-integration", firstProject)
                        .session(second.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(repository("other", "repository")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("project_not_found"));
    }

    @Test
    void rejectsDuplicateIntegrationAndRepositoryWithinTenant() throws Exception {
        RegisteredOwner owner = registerAndLogin("owner@example.com", "owner-password");
        UUID firstProject = createProject(owner.session(), "First");
        UUID secondProject = createProject(owner.session(), "Second");
        configure(owner.session(), firstProject, "Acme", "ReleaseFlow")
                .andExpect(status().isCreated());

        configure(owner.session(), firstProject, "other", "repository")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("github_integration_already_configured"));
        configure(owner.session(), secondProject, " ACME ", " releaseflow ")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("github_repository_already_connected"));
    }

    @Test
    void enforcesAuthenticationCsrfAndValidation() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("authentication_required"));

        RegisteredOwner owner = registerAndLogin("owner@example.com", "owner-password");
        mockMvc.perform(post("/api/projects")
                        .session(owner.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Project\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
        mockMvc.perform(post("/api/projects")
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors.name").exists());

        UUID projectId = createProject(owner.session(), "Project");
        configure(owner.session(), projectId, "bad/owner", "repository")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors.owner").exists());
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

    private org.springframework.test.web.servlet.ResultActions configure(
            MockHttpSession session,
            UUID projectId,
            String owner,
            String repository
    ) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/github-integration", projectId)
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(repository(owner, repository)));
    }

    private RegisteredOwner registerAndLogin(String email, String password) throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName(email);
        request.setDisplayName(email);
        request.setEmail(email);
        request.setPassword(password);
        RegistrationResult registration = registrationService.register(request);
        MvcResult login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().isFound())
                .andReturn();
        return new RegisteredOwner(registration, (MockHttpSession) login.getRequest().getSession(false));
    }

    private static String repository(String owner, String repository) {
        return "{\"owner\":\"%s\",\"repository\":\"%s\"}".formatted(owner, repository);
    }

    private record RegisteredOwner(RegistrationResult registration, MockHttpSession session) {
    }
}
