package com.hoangluongtran0309.releaseflow.configuration;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renders every page a signed-in person reaches, and the public ones, in Vietnamese. A key
 * with no translation is served as the key itself, so a leaked {@code ui.}, {@code error.}
 * or {@code validation.} key is what a missing or misspelled translation looks like here.
 */
class VietnameseRenderingIntegrationTest extends PostgreSqlIntegrationTest {

    /** What an unresolved message key looks like once it reaches the page. */
    private static final Pattern LEAKED_KEY =
            Pattern.compile("(?<![\\w.-])(?:ui|error|validation)\\.[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+");

    private static final Cookie VIETNAMESE = new Cookie("releaseflow_lang", "vi");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM releases");
        jdbcTemplate.update("DELETE FROM organization_invitations");
        jdbcTemplate.update("DELETE FROM integration_sources");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void everyPageRendersInVietnameseWithoutLeakingAKey() throws Exception {
        MockHttpSession session = registerAndLogin();
        UUID projectId = createProject(session);
        String sourceCreated = connectSource(session, projectId);
        String releasePath = createRelease(session, projectId);
        String invitationIssued = inviteMember(session);
        mockMvc.perform(post("/organization/slug").session(session).with(csrf()).param("slug", "acme"))
                .andExpect(status().isFound());

        List<String> workspacePages = List.of(
                "/",
                "/changes?project=" + projectId,
                "/releases?project=" + projectId,
                releasePath,
                "/projects",
                "/projects/" + projectId + "/sensitive-paths",
                "/members",
                "/audiences",
                "/audiences/new",
                "/categories",
                "/automation"
        );
        for (String page : workspacePages) {
            assertNoLeakedKey(page, get(page).session(session).cookie(VIETNAMESE));
        }

        // Two pages are only ever reached once, right after the thing they report.
        assertNoLeakedKey("source-created", post("/projects/{projectId}/sources", projectId)
                .session(session).with(csrf()).cookie(VIETNAMESE)
                .param("owner", "acme").param("repository", "second-" + sourceCreated));
        assertThat(invitationIssued).contains("Tôi đã gửi liên kết");

        List<String> publicPages = List.of("/", "/login", "/register", "/accept-invite", "/changelog/acme");
        for (String page : publicPages) {
            assertNoLeakedKey(page, get(page).cookie(VIETNAMESE));
        }

        // An unusable invitation link is the one page whose only content is a failure.
        MvcResult invalid = mockMvc.perform(post("/accept-invite/review")
                        .with(csrf()).cookie(VIETNAMESE).param("token", "not-a-token"))
                .andExpect(status().isBadRequest())
                .andReturn();
        String body = invalid.getResponse().getContentAsString();
        assertThat(leakedKeys(body)).as("untranslated keys on an unusable invitation").isEmpty();
        assertThat(body).contains("Liên kết mời này không hợp lệ hoặc đã hết hạn.");
    }

    private void assertNoLeakedKey(String page, MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().is2xxSuccessful()).andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(leakedKeys(body)).as("untranslated keys on %s", page).isEmpty();
        // A page that rendered nothing would pass the check above for the wrong reason.
        assertThat(body).as("%s rendered a page", page).contains("</html>");
    }

    private static List<String> leakedKeys(String body) {
        List<String> found = new ArrayList<>();
        Matcher matcher = LEAKED_KEY.matcher(body);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }

    private MockHttpSession registerAndLogin() throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName("Acme");
        request.setDisplayName("Owner");
        request.setEmail("owner@example.com");
        request.setPassword("owner-password");
        registrationService.register(request);
        MvcResult login = mockMvc.perform(post("/login").with(csrf())
                        .param("email", "owner@example.com")
                        .param("password", "owner-password"))
                .andExpect(status().isFound())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private UUID createProject(MockHttpSession session) throws Exception {
        mockMvc.perform(post("/projects").session(session).with(csrf()).param("name", "Checkout"))
                .andExpect(status().isFound());
        MvcResult projects = mockMvc.perform(get("/projects").session(session)).andReturn();
        Matcher matcher = Pattern.compile("id=\"project-([0-9a-f-]{36})\"")
                .matcher(projects.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        return UUID.fromString(matcher.group(1));
    }

    private String connectSource(MockHttpSession session, UUID projectId) throws Exception {
        mockMvc.perform(post("/projects/{projectId}/sources", projectId)
                        .session(session).with(csrf())
                        .param("owner", "acme").param("repository", "releaseflow"))
                .andExpect(status().isOk());
        return "releaseflow";
    }

    private String createRelease(MockHttpSession session, UUID projectId) throws Exception {
        MvcResult created = mockMvc.perform(post("/projects/{projectId}/releases", projectId)
                        .session(session).with(csrf())
                        .param("version", "1.4.0")
                        .param("summary", "Exports and a faster inbox."))
                .andExpect(status().isFound())
                .andReturn();
        return created.getResponse().getRedirectedUrl();
    }

    private String inviteMember(MockHttpSession session) throws Exception {
        MvcResult issued = mockMvc.perform(post("/members/invitations")
                        .session(session).with(csrf()).cookie(VIETNAMESE)
                        .param("email", "teammate@example.com"))
                .andExpect(status().isOk())
                .andReturn();
        String body = issued.getResponse().getContentAsString();
        assertThat(leakedKeys(body)).as("untranslated keys on invitation-issued").isEmpty();
        return body;
    }
}
