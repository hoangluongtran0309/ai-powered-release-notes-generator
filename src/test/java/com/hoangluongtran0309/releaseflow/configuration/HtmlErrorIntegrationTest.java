package com.hoangluongtran0309.releaseflow.configuration;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A request that fails outside a controller with its own answer. A browser gets a page; a
 * REST client keeps the Problem Details it would have got before.
 */
class HtmlErrorIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM organization_invitations");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void anUnknownAddressAnswersABrowserWithAPage() throws Exception {
        MockHttpSession session = registerAndLogin();

        mockMvc.perform(get("/nope").session(session).accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(content().string(Matchers.containsString("That page does not exist")))
                .andExpect(content().string(Matchers.containsString("/nope")))
                // Never the exception that brought it here.
                .andExpect(content().string(Matchers.not(Matchers.containsStringIgnoringCase("exception"))));
    }

    @Test
    void anUnknownAddressAnswersEverybodyElseWithProblemDetails() throws Exception {
        MockHttpSession session = registerAndLogin();

        mockMvc.perform(get("/nope").session(session).accept(MediaType.ALL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));

        // An address under /api is an API address whatever it says it accepts.
        mockMvc.perform(get("/api/nope").session(session).accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void theErrorPageIsWrittenInTheReadersLanguage() throws Exception {
        MockHttpSession session = registerAndLogin();

        mockMvc.perform(get("/nope").session(session)
                        .accept(MediaType.TEXT_HTML)
                        .cookie(new Cookie("releaseflow_lang", "vi")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(Matchers.containsString("Trang này không tồn tại")));

        mockMvc.perform(get("/nope").session(session)
                        .accept(MediaType.TEXT_HTML)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "vi"))
                .andExpect(content().string(Matchers.containsString("Trang này không tồn tại")));
    }

    /**
     * The page a member meets is rendered by the container's error dispatch, which MockMvc
     * does not run — {@code ui-smoke.spec.js} checks the page itself in a real browser.
     * What belongs here is that the refusal happens at all, and shows nothing of the page.
     */
    @Test
    void aPageThatIsNotThisRolesIsRefusedWithoutShowingIt() throws Exception {
        MockHttpSession session = registerAndLogin();
        MockHttpSession member = inviteAndJoinAsMember(session);

        mockMvc.perform(get("/automation").session(member).accept(MediaType.TEXT_HTML))
                .andExpect(status().isForbidden())
                .andExpect(content().string(Matchers.not(Matchers.containsString("Run history"))));
    }

    private MockHttpSession registerAndLogin() throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName("Acme");
        request.setDisplayName("Owner");
        request.setEmail("owner@example.com");
        request.setPassword("owner-password");
        registrationService.register(request);
        return login("owner@example.com", "owner-password");
    }

    private MockHttpSession inviteAndJoinAsMember(MockHttpSession admin) throws Exception {
        MvcResult issued = mockMvc.perform(post("/members/invitations").session(admin).with(csrf())
                        .param("email", "teammate@example.com"))
                .andExpect(status().isOk())
                .andReturn();
        String body = issued.getResponse().getContentAsString();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("accept-invite#token=([A-Za-z0-9_-]+)")
                .matcher(body);
        org.assertj.core.api.Assertions.assertThat(matcher.find()).as("the link is shown once").isTrue();

        mockMvc.perform(post("/accept-invite").with(csrf())
                        .param("token", matcher.group(1))
                        .param("displayName", "Teammate")
                        .param("password", "teammate-password"))
                .andExpect(status().isFound());
        return login("teammate@example.com", "teammate-password");
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/login").with(csrf())
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().isFound())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }
}
