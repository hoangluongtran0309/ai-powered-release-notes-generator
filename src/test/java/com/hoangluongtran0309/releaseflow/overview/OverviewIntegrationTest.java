package com.hoangluongtran0309.releaseflow.overview;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class OverviewIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String PROJECT_COOKIE = "releaseflow_project";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private OverviewService overviewService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM release_changes");
        jdbcTemplate.update("DELETE FROM releases");
        jdbcTemplate.update("DELETE FROM integration_sources");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void theOverviewOffersTheFirstUnfinishedStepAndNothingElse() throws Exception {
        MockHttpSession session = registerAndLogin();

        // Nothing exists yet, so the only thing that can be done is create a Project.
        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("overview"))
                .andExpect(content().string(Matchers.containsString("Create a project")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Waiting for the first change"))));

        UUID projectId = createProject(session, "Checkout");
        mockMvc.perform(get("/").session(session))
                .andExpect(content().string(Matchers.containsString("Connect a source")));

        connectSource(session, projectId);
        mockMvc.perform(get("/").session(session))
                .andExpect(content().string(Matchers.containsString("Waiting for the first change")));

        // A draft changes nothing while there is still nothing to put in it. Which stage
        // wins once changes exist is NextStepTest's business, not a page's.
        createRelease(session, projectId);
        mockMvc.perform(get("/").session(session))
                .andExpect(content().string(Matchers.containsString("Waiting for the first change")));
    }

    @Test
    void theFiguresCountThisProjectAndNotAnother() throws Exception {
        MockHttpSession session = registerAndLogin();
        UUID projectId = createProject(session, "Checkout");
        UUID other = createProject(session, "Ledger");
        createRelease(session, projectId);

        UUID organizationId = organizationId(session);
        OverviewView overview = overviewService.overview(organizationId, projectId);
        assertThat(overview.projectId()).isEqualTo(projectId);
        assertThat(overview.releases()).isEqualTo(1);
        assertThat(overview.changes()).isZero();
        assertThat(overview.projects()).hasSize(2);

        assertThat(overviewService.overview(organizationId, other).releases()).isZero();
    }

    @Test
    void aPageWithNoProjectInItsUrlOpensOnTheOneLastLookedAt() throws Exception {
        MockHttpSession session = registerAndLogin();
        UUID first = createProject(session, "First");
        UUID second = createProject(session, "Second");

        // Looking at one records it; the next page with no Project in its URL opens on it.
        mockMvc.perform(get("/changes").session(session).param("project", second.toString()))
                .andExpect(status().isOk())
                .andExpect(cookie().value(PROJECT_COOKIE, second.toString()))
                .andExpect(cookie().httpOnly(PROJECT_COOKIE, true));

        mockMvc.perform(get("/releases").session(session).cookie(new Cookie(PROJECT_COOKIE, second.toString())))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("value=\"" + second + "\" selected")));

        // A URL that names one still wins over the memory.
        mockMvc.perform(get("/releases").session(session)
                        .cookie(new Cookie(PROJECT_COOKIE, second.toString()))
                        .param("project", first.toString()))
                .andExpect(cookie().value(PROJECT_COOKIE, first.toString()));
    }

    @Test
    void aRememberedProjectThatIsNotThisOrganizationsIsSimplyIgnored() throws Exception {
        MockHttpSession session = registerAndLogin();
        UUID projectId = createProject(session, "Checkout");

        // A cookie is a memory, not an authority: one naming a Project this Organization
        // does not own falls back to its own first Project rather than failing.
        mockMvc.perform(get("/changes").session(session)
                        .cookie(new Cookie(PROJECT_COOKIE, UUID.randomUUID().toString())))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(projectId.toString())));

        mockMvc.perform(get("/changes").session(session).cookie(new Cookie(PROJECT_COOKIE, "not-a-uuid")))
                .andExpect(status().isOk());
    }

    @Test
    void aUrlThatNamesAnotherOrganizationsProjectIsStillNotFound() throws Exception {
        MockHttpSession session = registerAndLogin();
        UUID own = createProject(session, "Checkout");

        // The memory never stands in for what a URL asked for: a Project of another
        // Organization is reported, not quietly swapped for one of this Organization's.
        mockMvc.perform(get("/changes").session(session)
                        .cookie(new Cookie(PROJECT_COOKIE, own.toString()))
                        .param("project", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(Matchers.containsString("Project was not found.")));

        // And such a URL is not remembered either.
        mockMvc.perform(get("/changes").session(session)
                        .param("project", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(cookie().doesNotExist(PROJECT_COOKIE));
    }

    private UUID organizationId(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/session").session(session)).andReturn();
        Matcher matcher = Pattern.compile("\"organizationId\":\"([0-9a-f-]{36})\"")
                .matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).as("the session endpoint names the Organization").isTrue();
        return UUID.fromString(matcher.group(1));
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

    private UUID createProject(MockHttpSession session, String name) throws Exception {
        mockMvc.perform(post("/projects").session(session).with(csrf()).param("name", name))
                .andExpect(status().isFound());
        MvcResult projects = mockMvc.perform(get("/projects").session(session)).andReturn();
        Matcher matcher = Pattern.compile("id=\"project-([0-9a-f-]{36})\"[\\s\\S]{0,400}?>" + name + "<")
                .matcher(projects.getResponse().getContentAsString());
        assertThat(matcher.find()).as("project %s is on the page", name).isTrue();
        return UUID.fromString(matcher.group(1));
    }

    private void connectSource(MockHttpSession session, UUID projectId) throws Exception {
        mockMvc.perform(post("/projects/{projectId}/sources", projectId).session(session).with(csrf())
                        .param("owner", "acme").param("repository", "releaseflow"))
                .andExpect(status().isOk());
    }

    private void createRelease(MockHttpSession session, UUID projectId) throws Exception {
        mockMvc.perform(post("/projects/{projectId}/releases", projectId).session(session).with(csrf())
                        .param("version", "1.0.0"))
                .andExpect(status().isFound());
    }
}
