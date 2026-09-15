package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class AudiencePageIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM app_users");
        deleteAudiences();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void administratorsManageAudiencesFromThePage() throws Exception {
        Admin admin = registerAndLogin();

        mockMvc.perform(get("/audiences").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(view().name("audiences"))
                .andExpect(content().string(containsString("href=\"/audiences\"")))
                .andExpect(content().string(matchesPattern("(?s).*id=\"audience-list\".*Contributor.*End user.*Operator.*")))
                .andExpect(content().string(containsString("3 of 20 audiences")))
                .andExpect(content().string(containsString("data-variable=\"narrative\"")))
                .andExpect(content().string(containsString("x-data=\"audienceEditor()\"")))
                .andExpect(content().string(containsString("Reset to preset")));

        mockMvc.perform(post("/audiences").session(admin.session()).with(csrf())
                        .param("code", "board")
                        .param("displayName", "Board")
                        .param("templateBody", "{{#whatChanged}}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("This template is not valid Mustache")))
                .andExpect(content().string(containsString("value=\"board\"")));
        mockMvc.perform(post("/audiences").session(admin.session()).with(csrf())
                        .param("code", "operator")
                        .param("displayName", "Ops again")
                        .param("templateBody", "{{whatChanged}}"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("An audience with this code already exists.")));

        MvcResult created = mockMvc.perform(post("/audiences").session(admin.session()).with(csrf())
                        .param("code", "Board")
                        .param("displayName", "Board")
                        .param("communicationIntent", "Money and risk.")
                        .param("templateBody", "* {{whatChanged}}\r\n"))
                .andExpect(redirectedUrlPattern("/audiences/*?saved"))
                .andReturn();
        String audiencePath = created.getResponse().getRedirectedUrl().replace("?saved", "");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT template_body FROM audience_definitions WHERE code = 'board'", String.class)).isEqualTo("* {{whatChanged}}\n");

        mockMvc.perform(post("/audiences/preview").session(admin.session()).with(csrf())
                        .param("audienceId", audiencePath.substring("/audiences/".length()))
                        .param("displayName", "Board")
                        .param("templateBody", "## {{whatChanged}}\n\n<i>{{narrative}}</i>"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"audience-preview\"")))
                .andExpect(content().string(containsString("<h2>A safer deployment workflow is now available.</h2>")))
                .andExpect(content().string(containsString("&lt;i&gt;Release managers")))
                .andExpect(content().string(not(containsString("<i>Release managers"))));

        mockMvc.perform(post(audiencePath).session(admin.session()).with(csrf())
                        .param("displayName", "")
                        .param("templateBody", "{{whatChanged}}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Display name is required.")));
        mockMvc.perform(post(audiencePath + "/reset-to-preset").session(admin.session()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Only a shipped audience can be reset.")));
        mockMvc.perform(post(audiencePath + "/delete").session(admin.session()).with(csrf()))
                .andExpect(redirectedUrl("/audiences?deleted"));
        mockMvc.perform(get(audiencePath).session(admin.session()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Audience was not found.")));
    }

    @Test
    void membersDoNotSeeTheAudiencesPage() throws Exception {
        Admin admin = registerAndLogin();
        jdbcTemplate.update(
                """
                        INSERT INTO app_users (id, organization_id, email, password_hash, display_name, role, created_at)
                        VALUES (?, ?, 'member@example.com', ?, 'Member', 'MEMBER', now())
                        """,
                UUID.randomUUID(), admin.organizationId(), passwordEncoder.encode("member-password")
        );
        MockHttpSession member = login("member@example.com", "member-password");

        mockMvc.perform(get("/").session(member))
                .andExpect(content().string(not(containsString("href=\"/audiences\""))));
        mockMvc.perform(get("/audiences/new").session(member)).andExpect(status().isForbidden());
        mockMvc.perform(post("/audiences").session(member).with(csrf()).param("code", "x")).andExpect(status().isForbidden());
    }

    private Admin registerAndLogin() throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName("Acme");
        request.setDisplayName("Mai Tran");
        request.setEmail("owner@example.com");
        request.setPassword("owner-password");
        RegistrationResult registration = registrationService.register(request);
        return new Admin(registration.organizationId(), login("owner@example.com", "owner-password"));
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

    private record Admin(UUID organizationId, MockHttpSession session) {
    }
}
