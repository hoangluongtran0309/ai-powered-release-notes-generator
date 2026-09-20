package com.hoangluongtran0309.releaseflow.changelog;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With a wildcard domain configured, an Organization's changelog answers at
 * {@code {slug}.{domain}} — and nothing else does.
 */
@TestPropertySource(properties = "releaseflow.public.changelog-base-domain=changelog.example.test")
class PublicChangelogHostRoutingIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
        register();
    }

    @Test
    void servesTheChangelogOfTheOrganizationTheHostNames() throws Exception {
        mockMvc.perform(get("/").header("Host", "acme.changelog.example.test"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Release notes")))
                .andExpect(content().string(containsString("Acme")));

        mockMvc.perform(get("/rss.xml").header("Host", "acme.changelog.example.test"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<rss version=\"2.0\">")));

        // A slug nobody answers is not an invitation to look around.
        mockMvc.perform(get("/").header("Host", "nobody.changelog.example.test"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rewritesNothingElse() throws Exception {
        // Only GET, and only the three public paths: a POST falls through to the
        // ordinary application, which has nothing to post to at "/".
        mockMvc.perform(post("/").header("Host", "acme.changelog.example.test").with(csrf()))
                .andExpect(status().isMethodNotAllowed());
        // The signed-in application still answers for itself on that host: whoever asks
        // for a page of it is asked to sign in, not handed a changelog.
        mockMvc.perform(get("/projects").header("Host", "acme.changelog.example.test"))
                .andExpect(status().isUnauthorized());

        // Two labels beneath the domain is not an Organization of it.
        mockMvc.perform(get("/").header("Host", "a.acme.changelog.example.test"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ReleaseFlow")));

        // And the ordinary host still serves the ordinary application.
        mockMvc.perform(get("/").header("Host", "localhost"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ReleaseFlow")));
    }

    private void register() {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName("Acme");
        request.setDisplayName("Mai Tran");
        request.setEmail("owner@example.com");
        request.setPassword("owner-password");
        registrationService.register(request);
    }
}
