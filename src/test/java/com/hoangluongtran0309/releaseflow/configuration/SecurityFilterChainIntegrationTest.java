package com.hoangluongtran0309.releaseflow.configuration;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What each security chain promises, whoever asks. Cross-site request forgery is
 * refused everywhere a browser's session could be borrowed; the only paths excused are
 * the ones a signature proves, and they are the only paths those chains answer at all.
 */
class SecurityFilterChainIntegrationTest extends PostgreSqlIntegrationTest {

    // A well-formed signature over nothing in particular: enough to reach the verifier.
    private static final String SIGNATURE = "sha256=" + "0".repeat(64);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // A project outlives the test that made it unless it is swept up here, and the next
    // class to delete an Organization would find one still pointing at it.
    @BeforeEach
    @AfterEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void refusesAWriteFromASessionThatCarriesNoToken() throws Exception {
        MockHttpSession session = registerAndLogin();

        mockMvc.perform(post("/api/projects")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ReleaseFlow\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));

        mockMvc.perform(post("/api/projects")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ReleaseFlow\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void letsASignedWebhookPathThroughWithoutAToken() throws Exception {
        // No token, and the delivery still reaches the verifier that judges it: a
        // signature nobody can produce is 401, which is not the 403 a blocked
        // cross-site write would get.
        mockMvc.perform(post("/webhooks/github/{webhookId}", UUID.randomUUID())
                        .header("X-Hub-Signature-256", SIGNATURE)
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("webhook_signature_invalid"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void answersNothingUnderWebhooksThatIsNotOneOfItsOwnEndpoints() throws Exception {
        // A path nobody mapped is refused rather than published by accident.
        mockMvc.perform(post("/webhooks/unknown/{webhookId}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        // And a delivery endpoint answers the one method it was written for.
        mockMvc.perform(get("/webhooks/github/{webhookId}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    private void register() {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName("Acme");
        request.setDisplayName("Mai Tran");
        request.setEmail("owner@example.com");
        request.setPassword("owner-password");
        registrationService.register(request);
    }

    private MockHttpSession registerAndLogin() throws Exception {
        register();
        MvcResult login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", "owner@example.com")
                        .param("password", "owner-password"))
                .andExpect(status().isFound())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }
}
