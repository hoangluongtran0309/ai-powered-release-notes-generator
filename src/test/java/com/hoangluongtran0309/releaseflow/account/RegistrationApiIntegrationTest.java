package com.hoangluongtran0309.releaseflow.account;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegistrationApiIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearDatabase() {
        appUserRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void registersOrganizationOwnerAtomicallyAndCanonicalizesEmail() throws Exception {
        mockMvc.perform(post("/api/registrations")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistration("  Owner@Example.COM  ")))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.organizationName").value("Acme"))
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                .andExpect(jsonPath("$.displayName").value("Owner"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(organizationRepository.count()).isOne();
        assertThat(appUserRepository.count()).isOne();
        AppUser savedOwner = appUserRepository.findByEmail("owner@example.com").orElseThrow();
        assertThat(savedOwner.passwordHash()).isNotEqualTo("a-secure-password");
        assertThat(passwordEncoder.matches("a-secure-password", savedOwner.passwordHash())).isTrue();
    }

    @Test
    void rejectsCanonicalDuplicateWithoutCreatingAnotherOrganization() throws Exception {
        register("owner@example.com");

        mockMvc.perform(post("/api/registrations")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistration("OWNER@example.com")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("email_already_registered"));

        assertThat(organizationRepository.count()).isOne();
        assertThat(appUserRepository.count()).isOne();
    }

    @Test
    void rejectsInvalidInputWithStableProblemCode() throws Exception {
        mockMvc.perform(post("/api/registrations")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organizationName":"","displayName":"","email":"bad","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors.organizationName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void requiresCsrfForRestRegistration() throws Exception {
        mockMvc.perform(post("/api/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistration("owner@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("access_denied"));
    }

    @Test
    void exposesCsrfTokenForSessionBasedApiClients() throws Exception {
        mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.parameterName").value("_csrf"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void rejectsPasswordThatExceedsBcryptByteLimit() throws Exception {
        mockMvc.perform(post("/api/registrations")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationName": "Acme",
                                  "displayName": "Owner",
                                  "email": "owner@example.com",
                                  "password": "🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors.password").exists());
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/registrations")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistration(email)))
                .andExpect(status().isCreated());
    }

    private static String validRegistration(String email) {
        return """
                {
                  "organizationName": " Acme ",
                  "displayName": " Owner ",
                  "email": "%s",
                  "password": "a-secure-password"
                }
                """.formatted(email);
    }
}
