package com.hoangluongtran0309.releaseflow.account;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The address an Organization's public changelog answers on. */
class OrganizationSlugIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearDatabase() {
        appUserRepository.deleteAll();
        deleteOrganizationSettings();
        organizationRepository.deleteAll();
    }

    @Test
    void registeringMakesOneFromTheOrganizationsName() throws Exception {
        RegistrationResult first = register("Acme Tools", "owner@example.com");
        RegistrationResult second = register("Acme Tools", "second@example.com");
        RegistrationResult unnameable = register("公司", "third@example.com");

        assertThat(first.changelogSlug()).isEqualTo("acme-tools");
        // Whoever is first keeps the plain address; the next one is numbered.
        assertThat(second.changelogSlug()).isEqualTo("acme-tools-2");
        assertThat(unnameable.changelogSlug()).isEqualTo("org");
    }

    @Test
    void administratorsMoveTheChangelogAndNobodyElseCan() throws Exception {
        MockHttpSession session = registerAndLogin("Acme", "owner@example.com");

        mockMvc.perform(get("/api/organization/slug").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("acme"));

        mockMvc.perform(put("/api/organization/slug").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"  ACME-Tools \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("acme-tools"));

        mockMvc.perform(put("/api/organization/slug").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"acme tools\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("organization_slug_invalid"));

        register("Somebody Else", "other@example.com");
        mockMvc.perform(put("/api/organization/slug").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"somebody-else\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("organization_slug_taken"));

        // A member may read the page but not move the changelog.
        MockHttpSession member = memberSession(organizationId(session), "member@example.com");
        mockMvc.perform(put("/api/organization/slug").session(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"hijacked\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/organization/slug").session(member).with(csrf()).param("slug", "hijacked"))
                .andExpect(status().isForbidden());
    }

    @Test
    void theProjectsPageShowsTheChangelogAddressAndSavesIt() throws Exception {
        MockHttpSession session = registerAndLogin("Acme", "owner@example.com");

        mockMvc.perform(get("/projects").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Public changelog")))
                .andExpect(content().string(containsString("/changelog/acme")));

        mockMvc.perform(post("/organization/slug").session(session).with(csrf()).param("slug", "acme-tools"))
                .andExpect(redirectedUrl("/projects?changelogSaved"));
        assertThat(jdbcTemplate.queryForObject("SELECT slug FROM organizations", String.class))
                .isEqualTo("acme-tools");

        mockMvc.perform(post("/organization/slug").session(session).with(csrf()).param("slug", "not a label"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("cannot be a changelog address")));
    }

    private RegistrationResult register(String organizationName, String email) {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName(organizationName);
        request.setDisplayName("Mai Tran");
        request.setEmail(email);
        request.setPassword("owner-password");
        return registrationService.register(request);
    }

    private MockHttpSession registerAndLogin(String organizationName, String email) throws Exception {
        register(organizationName, email);
        return login(email, "owner-password");
    }

    private MockHttpSession memberSession(UUID organizationId, String email) throws Exception {
        jdbcTemplate.update(
                """
                        INSERT INTO app_users (id, organization_id, email, password_hash, display_name, role, created_at)
                        VALUES (?, ?, ?, ?, 'Member', 'MEMBER', now())
                        """,
                UUID.randomUUID(), organizationId, email, passwordEncoder.encode("member-password")
        );
        return login(email, "member-password");
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/login").with(csrf()).param("email", email).param("password", password))
                .andExpect(status().isFound())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private UUID organizationId(MockHttpSession session) throws Exception {
        String body = mockMvc.perform(get("/api/session").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(body, "$.organizationId"));
    }
}
