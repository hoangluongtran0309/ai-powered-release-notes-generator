package com.hoangluongtran0309.releaseflow.account;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.jayway.jsonpath.JsonPath;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class InvitationPageIntegrationTest extends PostgreSqlIntegrationTest {

    private static final Pattern LINK = Pattern.compile("value=\"(http[^\"]*/accept-invite#token=([A-Za-z0-9_-]{43}))\"");

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
        jdbcTemplate.update("DELETE FROM github_integrations");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void administratorInvitesAndTheInviteeJoinsThroughThePages() throws Exception {
        MockHttpSession admin = registerAndLogin("admin@example.com", "Acme");

        mockMvc.perform(get("/members").session(admin))
                .andExpect(status().isOk())
                .andExpect(view().name("members"))
                .andExpect(content().string(containsString("href=\"/members\"")))
                .andExpect(content().string(containsString("No invitations yet.")));

        MvcResult issued = mockMvc.perform(post("/members/invitations")
                        .session(admin)
                        .with(csrf())
                        .param("email", "teammate@example.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("invitation-issued"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn();
        Matcher link = LINK.matcher(issued.getResponse().getContentAsString());
        assertThat(link.find()).isTrue();
        String token = link.group(2);

        mockMvc.perform(get("/members").session(admin))
                .andExpect(content().string(containsString("teammate@example.com")))
                .andExpect(content().string(containsString(">Pending</span>")))
                .andExpect(content().string(not(containsString(token))));
        mockMvc.perform(post("/members/invitations").session(admin).with(csrf()).param("email", "teammate@example.com"))
                .andExpect(status().isConflict())
                .andExpect(model().attributeHasFieldErrors("invitationRequest", "email"));

        mockMvc.perform(get("/accept-invite"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().string(containsString("data-invitation-open=\"true\"")))
                .andExpect(content().string(containsString("action=\"/accept-invite/review\"")));
        mockMvc.perform(post("/accept-invite/review").with(csrf()).param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Join <span>Acme</span>")))
                .andExpect(content().string(containsString("teammate@example.com")))
                .andExpect(content().string(containsString("name=\"displayName\"")));
        mockMvc.perform(post("/accept-invite").with(csrf())
                        .param("token", token)
                        .param("displayName", "Lan Nguyen")
                        .param("password", "short"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("acceptRequest", "password"));
        mockMvc.perform(post("/accept-invite").with(csrf())
                        .param("token", token)
                        .param("displayName", "Lan Nguyen")
                        .param("password", "member-password-1"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?invited"));
        mockMvc.perform(get("/login").param("invited", ""))
                .andExpect(content().string(containsString("Your account is ready.")));

        MockHttpSession member = login("teammate@example.com", "member-password-1");
        mockMvc.perform(get("/members").session(member)).andExpect(status().isForbidden());
        mockMvc.perform(get("/projects").session(member))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/members\""))))
                .andExpect(content().string(containsString(">Member</p>")));
    }

    @Test
    void showsOneMessageForUnusableLinksAndHidesRepositorySetupFromMembers() throws Exception {
        MockHttpSession admin = registerAndLogin("admin@example.com", "Acme");
        mockMvc.perform(post("/accept-invite/review").with(csrf()).param("token", "x".repeat(43)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("This invitation link is invalid or has expired.")));

        String token = issue(admin, "teammate@example.com");
        UUID invitationId = jdbcTemplate.queryForObject("SELECT id FROM organization_invitations", UUID.class);
        mockMvc.perform(post("/members/invitations/{id}/revoke", invitationId).session(admin).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/members"));
        mockMvc.perform(post("/accept-invite/review").with(csrf()).param("token", token))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("This invitation link is invalid or has expired.")));
        mockMvc.perform(post("/members/invitations/{id}/reissue", invitationId).session(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("invitation-issued"));

        mockMvc.perform(post("/api/projects").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Acme app\"}"))
                .andExpect(status().isCreated());
        String newToken = issue(admin, "second@example.com");
        mockMvc.perform(post("/accept-invite").with(csrf())
                        .param("token", newToken)
                        .param("displayName", "Second Member")
                        .param("password", "member-password-1"))
                .andExpect(redirectedUrl("/login?invited"));
        MockHttpSession member = login("second@example.com", "member-password-1");
        mockMvc.perform(get("/projects").session(member))
                .andExpect(content().string(containsString("Only organization administrators can connect repositories.")))
                .andExpect(content().string(not(matchesPattern("(?s).*action=\"/projects/[^\"]+/github-integration\".*"))));
        mockMvc.perform(get("/projects").session(admin))
                .andExpect(content().string(matchesPattern("(?s).*action=\"/projects/[^\"]+/github-integration\".*")));
    }

    private String issue(MockHttpSession admin, String email) throws Exception {
        MvcResult issued = mockMvc.perform(post("/api/invitations")
                        .session(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        String path = JsonPath.read(issued.getResponse().getContentAsString(), "$.acceptancePath");
        return path.substring("/accept-invite#token=".length());
    }

    private MockHttpSession registerAndLogin(String email, String organizationName) throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName(organizationName);
        request.setDisplayName(email);
        request.setEmail(email);
        request.setPassword("admin-password-1");
        registrationService.register(request);
        return login(email, "admin-password-1");
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().isFound())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }
}
