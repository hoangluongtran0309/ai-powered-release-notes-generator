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
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvitationIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM organization_invitations");
        jdbcTemplate.update("DELETE FROM github_integrations");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteAudiences();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void invitesAMemberWhoJoinsTheInvitingOrganization() throws Exception {
        Account admin = registerAndLogin("admin@example.com", "Acme");

        String token = invite(admin, " Teammate@Example.com ")
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.email").value("teammate@example.com"))
                .andExpect(jsonPath("$.acceptancePath", startsWith("/accept-invite#token=")))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn().getResponse().getContentAsString()
                .transform(InvitationIntegrationTest::tokenOf);

        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(jdbcTemplate.queryForObject("SELECT token_hash FROM organization_invitations", String.class))
                .isEqualTo(InvitationToken.hash(token))
                .isNotEqualTo(token);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM organization_invitations WHERE token_hash LIKE ?", Long.class, "%" + token + "%"
        )).isZero();

        publicPost("/api/public/invitations/inspect", "{\"token\":\"%s\"}".formatted(token))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.email").value("teammate@example.com"))
                .andExpect(jsonPath("$.organizationName").value("Acme"));
        accept(token, "Lan Nguyen", "member-password-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("teammate@example.com"));

        MockHttpSession member = login("teammate@example.com", "member-password-1");
        mockMvc.perform(get("/api/session").session(member))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.displayName").value("Lan Nguyen"))
                .andExpect(jsonPath("$.organizationId").value(admin.organizationId().toString()));
        mockMvc.perform(get("/api/members").session(admin.session()))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[1].email").value("teammate@example.com"))
                .andExpect(jsonPath("$[1].role").value("MEMBER"));
        mockMvc.perform(get("/api/invitations").session(admin.session()))
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$[0].createdByName").value("admin@example.com"));

        expectInvalid(accept(token, "Someone Else", "another-password-1"));
    }

    @Test
    void rejectsDuplicateInvitationsAndExistingAccounts() throws Exception {
        Account admin = registerAndLogin("admin@example.com", "Acme");
        registerAndLogin("elsewhere@example.com", "Other");

        invite(admin, "teammate@example.com").andExpect(status().isCreated());
        invite(admin, "TEAMMATE@example.com")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("invitation_already_pending"));
        invite(admin, "elsewhere@example.com")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invitation_email_unavailable"));
        invite(admin, "not-an-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void reissuesRevokesAndExpiresInvitations() throws Exception {
        Account admin = registerAndLogin("admin@example.com", "Acme");
        String first = tokenOf(invite(admin, "teammate@example.com").andReturn().getResponse().getContentAsString());
        UUID invitationId = UUID.fromString(JsonPath.read(
                mockMvc.perform(get("/api/invitations").session(admin.session())).andReturn().getResponse().getContentAsString(),
                "$[0].id"
        ));

        String second = tokenOf(mockMvc.perform(post("/api/invitations/{id}/reissue", invitationId)
                        .session(admin.session())
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn().getResponse().getContentAsString());
        assertThat(second).isNotEqualTo(first);
        expectInvalid(publicPost("/api/public/invitations/inspect", "{\"token\":\"%s\"}".formatted(first)));
        mockMvc.perform(get("/api/invitations").session(admin.session()))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[1].status").value("REVOKED"));

        UUID secondId = UUID.fromString(JsonPath.read(
                mockMvc.perform(get("/api/invitations").session(admin.session())).andReturn().getResponse().getContentAsString(),
                "$[0].id"
        ));
        mockMvc.perform(delete("/api/invitations/{id}", secondId).session(admin.session()).with(csrf()))
                .andExpect(status().isNoContent());
        expectInvalid(accept(second, "Lan Nguyen", "member-password-1"));

        String third = tokenOf(invite(admin, "teammate@example.com").andReturn().getResponse().getContentAsString());
        jdbcTemplate.update("UPDATE organization_invitations SET expires_at = now() - interval '1 minute' WHERE status = 'PENDING'");
        mockMvc.perform(get("/api/invitations").session(admin.session()))
                .andExpect(jsonPath("$[0].status").value("EXPIRED"));
        expectInvalid(accept(third, "Lan Nguyen", "member-password-1"));
        invite(admin, "teammate@example.com").andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM organization_invitations WHERE status = 'EXPIRED'", Long.class
        )).isOne();
    }

    @Test
    void answersEveryUnusableTokenTheSameWay() throws Exception {
        Account admin = registerAndLogin("admin@example.com", "Acme");
        String token = tokenOf(invite(admin, "teammate@example.com").andReturn().getResponse().getContentAsString());

        String unknown = expectInvalid(accept("x".repeat(43), "Lan Nguyen", "member-password-1"))
                .andReturn().getResponse().getContentAsString();
        accept(token, "Lan Nguyen", "member-password-1").andExpect(status().isCreated());
        String used = expectInvalid(accept(token, "Lan Nguyen", "member-password-1"))
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(used, "$.detail")).isEqualTo(JsonPath.read(unknown, "$.detail"));
        accept(token, "Lan Nguyen", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void letsExactlyOneConcurrentAcceptanceSucceed() throws Exception {
        Account admin = registerAndLogin("admin@example.com", "Acme");
        String token = tokenOf(invite(admin, "teammate@example.com").andReturn().getResponse().getContentAsString());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                Callable<Boolean> task = () -> {
                    start.await();
                    AcceptInvitationRequest request = new AcceptInvitationRequest();
                    request.setToken(token);
                    request.setDisplayName("Lan Nguyen");
                    request.setPassword("member-password-1");
                    try {
                        invitationService.accept(request);
                        return true;
                    } catch (InvalidInvitationException | InvitationEmailUnavailableException exception) {
                        return false;
                    }
                };
                results.add(executor.submit(task));
            }
            start.countDown();
            long successes = 0;
            for (Future<Boolean> result : results) {
                successes += result.get() ? 1 : 0;
            }
            assertThat(successes).isOne();
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app_users WHERE email = 'teammate@example.com'", Long.class
        )).isOne();
    }

    @Test
    void keepsAdministrationToAdminsOfTheSameOrganization() throws Exception {
        Account admin = registerAndLogin("admin@example.com", "Acme");
        Account otherAdmin = registerAndLogin("other@example.com", "Other");
        String token = tokenOf(invite(admin, "teammate@example.com").andReturn().getResponse().getContentAsString());
        UUID invitationId = UUID.fromString(JsonPath.read(
                mockMvc.perform(get("/api/invitations").session(admin.session())).andReturn().getResponse().getContentAsString(),
                "$[0].id"
        ));
        accept(token, "Lan Nguyen", "member-password-1").andExpect(status().isCreated());
        MockHttpSession member = login("teammate@example.com", "member-password-1");

        mockMvc.perform(get("/api/members").session(member))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
        mockMvc.perform(get("/api/invitations").session(member)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/invitations").session(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"x@example.com\"}"))
                .andExpect(status().isForbidden());

        MvcResult project = mockMvc.perform(post("/api/projects").session(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Member project\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID projectId = UUID.fromString(JsonPath.read(project.getResponse().getContentAsString(), "$.id"));
        mockMvc.perform(post("/api/projects/{id}/github-integration", projectId).session(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"owner\":\"acme\",\"repository\":\"app\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/projects/{id}/github-integration", projectId).session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"owner\":\"acme\",\"repository\":\"app\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/invitations").session(otherAdmin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(delete("/api/invitations/{id}", invitationId).session(otherAdmin.session()).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("invitation_not_found"));
        mockMvc.perform(post("/api/invitations/{id}/reissue", invitationId).session(otherAdmin.session()).with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/public/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"displayName\":\"X\",\"password\":\"member-password-1\"}".formatted(token)))
                .andExpect(status().isForbidden());
    }

    private ResultActions invite(Account admin, String email) throws Exception {
        return mockMvc.perform(post("/api/invitations")
                .session(admin.session())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)));
    }

    private ResultActions accept(String token, String displayName, String password) throws Exception {
        return publicPost(
                "/api/public/invitations/accept",
                "{\"token\":\"%s\",\"displayName\":\"%s\",\"password\":\"%s\"}".formatted(token, displayName, password)
        );
    }

    private ResultActions publicPost(String path, String body) throws Exception {
        return mockMvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static ResultActions expectInvalid(ResultActions result) throws Exception {
        return result.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("invitation_invalid"));
    }

    private static String tokenOf(String issuedJson) {
        String path = JsonPath.read(issuedJson, "$.acceptancePath");
        return path.substring("/accept-invite#token=".length());
    }

    private Account registerAndLogin(String email, String organizationName) throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName(organizationName);
        request.setDisplayName(email);
        request.setEmail(email);
        request.setPassword("admin-password-1");
        RegistrationResult registration = registrationService.register(request);
        return new Account(registration.organizationId(), login(email, "admin-password-1"));
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

    private record Account(UUID organizationId, MockHttpSession session) {
    }
}
