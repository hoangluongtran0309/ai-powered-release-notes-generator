package com.hoangluongtran0309.releaseflow.account;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.validation.BindingResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class RegistrationPageIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @BeforeEach
    void clearDatabase() {
        appUserRepository.deleteAll();
        deleteAudiences();
        organizationRepository.deleteAll();
    }

    @Test
    void registrationAndLoginPagesRenderCsrfProtectedForms() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
    }

    @Test
    void validFormCreatesOwnerAndRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("organizationName", "Acme")
                        .param("displayName", "Owner")
                        .param("email", "owner@example.com")
                        .param("password", "a-secure-password"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?registered"));

        assertThat(organizationRepository.count()).isOne();
        assertThat(appUserRepository.count()).isOne();
    }

    @Test
    void invalidFormRendersFieldErrorsWithoutWritingData() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("organizationName", "")
                        .param("displayName", "")
                        .param("email", "bad")
                        .param("password", "short"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrors(
                        "registration", "organizationName", "displayName", "email", "password"
                ));

        assertThat(organizationRepository.count()).isZero();
        assertThat(appUserRepository.count()).isZero();
    }

    @Test
    void blankPasswordReportsASingleRequiredError() throws Exception {
        MvcResult result = mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("organizationName", "Acme")
                        .param("displayName", "Owner")
                        .param("email", "owner@example.com")
                        .param("password", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrorCode("registration", "password", "NotBlank"))
                .andReturn();

        BindingResult bindingResult = (BindingResult) result.getModelAndView().getModel()
                .get(BindingResult.MODEL_KEY_PREFIX + "registration");
        assertThat(bindingResult.getFieldErrors("password")).hasSize(1);
    }

    @Test
    void duplicateEmailRendersFieldErrorWithoutCreatingOrphanOrganization() throws Exception {
        submitValidForm("owner@example.com")
                .andExpect(status().isFound());

        submitValidForm("OWNER@example.com")
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrors("registration", "email"));

        assertThat(organizationRepository.count()).isOne();
        assertThat(appUserRepository.count()).isOne();
    }

    private org.springframework.test.web.servlet.ResultActions submitValidForm(String email) throws Exception {
        return mockMvc.perform(post("/register")
                .with(csrf())
                .param("organizationName", "Acme")
                .param("displayName", "Owner")
                .param("email", email)
                .param("password", "a-secure-password"));
    }
}
