package com.hoangluongtran0309.releaseflow.account;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class AuthenticationIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @BeforeEach
    void clearDatabase() {
        appUserRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void anonymousSessionRequestReturnsProblemJsonInsteadOfRedirect() throws Exception {
        mockMvc.perform(get("/api/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("authentication_required"));
    }

    @Test
    void ownerCanSignInWithCanonicalizedEmailAndReadOnlyOwnTenantSession() throws Exception {
        RegistrationResult first = registrationService.register(registration(
                "First Organization", "First Owner", "first@example.com", "first-password"
        ));
        RegistrationResult second = registrationService.register(registration(
                "Second Organization", "Second Owner", "second@example.com", "second-password"
        ));

        MvcResult login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", " FIRST@EXAMPLE.COM ")
                        .param("password", "first-password"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(first.userId().toString()))
                .andExpect(jsonPath("$.organizationId").value(first.organizationId().toString()))
                .andExpect(jsonPath("$.email").value("first@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        assertThat(appUserRepository.findByIdAndOrganizationId(first.userId(), second.organizationId())).isEmpty();
        assertThat(appUserRepository.findByIdAndOrganizationId(first.userId(), first.organizationId())).isPresent();
    }

    @Test
    void wrongPasswordDoesNotCreateAuthenticatedSession() throws Exception {
        registrationService.register(registration(
                "Acme", "Owner", "owner@example.com", "correct-password"
        ));

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", "owner@example.com")
                        .param("password", "wrong-password"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void signedInOwnerSeesWorkspaceOverviewAtRoot() throws Exception {
        registrationService.register(registration(
                "Acme", "Owner", "owner@example.com", "correct-password"
        ));
        MvcResult login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", "owner@example.com")
                        .param("password", "correct-password"))
                .andExpect(status().isFound())
                .andReturn();

        mockMvc.perform(get("/").session((MockHttpSession) login.getRequest().getSession(false)))
                .andExpect(status().isOk())
                .andExpect(view().name("overview"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Welcome, Owner")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("owner@example.com")));
    }

    private static RegistrationRequest registration(
            String organizationName,
            String displayName,
            String email,
            String password
    ) {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName(organizationName);
        request.setDisplayName(displayName);
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
