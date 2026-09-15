package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.account.AppUserRepository;
import com.hoangluongtran0309.releaseflow.account.OrganizationRepository;
import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class ProjectPageIntegrationTest extends PostgreSqlIntegrationTest {

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

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        integrationRepository.deleteAll();
        projectRepository.deleteAll();
        appUserRepository.deleteAll();
        deleteOrganizationSettings();
        organizationRepository.deleteAll();
    }

    @Test
    void rendersProjectManagementAndCreatesProjectWithCsrf() throws Exception {
        MockHttpSession session = registerAndLogin("owner@example.com", "owner-password");

        mockMvc.perform(get("/projects").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("projects"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));

        mockMvc.perform(post("/projects")
                        .session(session)
                        .with(csrf())
                        .param("name", " ReleaseFlow "))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/projects"));

        assertThat(projectRepository.findAll()).singleElement()
                .extracting(Project::getName)
                .isEqualTo("ReleaseFlow");
        mockMvc.perform(get("/projects").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ReleaseFlow")))
                .andExpect(content().string(org.hamcrest.Matchers.matchesPattern(
                        "(?s).*>\\d{1,2} [A-Z][a-z]{2} \\d{4}, \\d{2}:\\d{2} UTC</time>.*"
                )));
    }

    @Test
    void rendersRepositoryConflictInsideTheSubmittedProjectCardAndKeepsInput() throws Exception {
        MockHttpSession session = registerAndLogin("owner@example.com", "owner-password");
        UUID connected = createProject(session, "Connected");
        UUID duplicate = createProject(session, "Duplicate");
        mockMvc.perform(post("/projects/{projectId}/github-integration", connected)
                        .session(session)
                        .with(csrf())
                        .param("owner", "acme")
                        .param("repository", "releaseflow"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/projects/{projectId}/github-integration", duplicate)
                        .session(session)
                        .with(csrf())
                        .param("owner", " Acme ")
                        .param("repository", "ReleaseFlow"))
                .andExpect(status().isConflict())
                .andExpect(view().name("projects"))
                .andExpect(model().attributeDoesNotExist("pageError"))
                .andExpect(content().string(org.hamcrest.Matchers.matchesPattern(
                        "(?s).*id=\"project-" + duplicate + "\".*"
                                + "This GitHub repository is already connected to another project\\..*"
                                + "value=\"Acme\".*value=\"ReleaseFlow\".*"
                )));

        assertThat(integrationRepository.count()).isOne();
    }

    @Test
    void revealsGeneratedSecretOnceInANonCacheableResponse() throws Exception {
        MockHttpSession session = registerAndLogin("owner@example.com", "owner-password");
        UUID projectId = createProject(session, "ReleaseFlow");

        mockMvc.perform(post("/projects/{projectId}/github-integration", projectId)
                        .session(session)
                        .with(csrf())
                        .param("owner", " Acme ")
                        .param("repository", " ReleaseFlow "))
                .andExpect(status().isOk())
                .andExpect(view().name("github-integration-created"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(model().attributeExists("integration"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("The secret will not be shown again")))
                .andExpect(content().string(org.hamcrest.Matchers.matchesPattern("(?s).*[A-Za-z0-9_-]{43}.*")));

        mockMvc.perform(get("/projects").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("acme/releaseflow")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Waiting for the first signed delivery")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No verified delivery yet")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Webhook secret</dt>")
                )));
    }

    @Test
    void rendersValidationAndCrossTenantErrorsWithoutWritingIntegration() throws Exception {
        MockHttpSession first = registerAndLogin("first@example.com", "first-password");
        UUID firstProject = createProject(first, "First");
        MockHttpSession second = registerAndLogin("second@example.com", "second-password");

        mockMvc.perform(post("/projects/{projectId}/github-integration", firstProject)
                        .session(second)
                        .with(csrf())
                        .param("owner", "acme")
                        .param("repository", "releaseflow"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("projects"))
                .andExpect(model().attributeHasErrors("githubIntegrationRequest"))
                .andExpect(model().attribute("pageError", "Project was not found."))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Project was not found.")));

        UUID secondProject = createProject(second, "Second");
        mockMvc.perform(post("/projects/{projectId}/github-integration", secondProject)
                        .session(second)
                        .with(csrf())
                        .param("owner", "bad/owner")
                        .param("repository", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("projects"))
                .andExpect(model().attributeHasFieldErrors(
                        "githubIntegrationRequest", "owner", "repository"
                ))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"bad/owner\"")));

        assertThat(integrationRepository.count()).isZero();
    }

    private UUID createProject(MockHttpSession session, String name) throws Exception {
        mockMvc.perform(post("/projects")
                        .session(session)
                        .with(csrf())
                        .param("name", name))
                .andExpect(status().isFound());
        return projectRepository.findAll().stream()
                .filter(project -> project.getName().equals(name))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private MockHttpSession registerAndLogin(String email, String password) throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName(email);
        request.setDisplayName(email);
        request.setEmail(email);
        request.setPassword(password);
        registrationService.register(request);
        MvcResult login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().isFound())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }
}
