package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AudienceIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String TEMPLATE = "- {{whatChanged}}{{#narrative}} — {{.}}{{/narrative}}";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        jdbcTemplate.execute("TRUNCATE release_audience_notes, release_change_reviews, release_notes, release_changes, releases");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteAudiences();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void registrationSeedsTheShippedAudiencesInTheOutputLanguage() throws Exception {
        Admin english = registerAndLogin("owner@example.com", "en");
        Admin vietnamese = registerAndLogin("chu@example.com", "vi");

        mockMvc.perform(get("/api/audiences").session(english.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code").value(contains("contributor", "end_user", "operator")))
                .andExpect(jsonPath("$[*].displayName").value(contains("Contributor", "End user", "Operator")))
                .andExpect(jsonPath("$[0].preset").value(true))
                .andExpect(jsonPath("$[0].communicationIntent", containsString("implementation detail")))
                .andExpect(jsonPath("$[2].templateBody", containsString("  - Detail: {{.}}")));
        mockMvc.perform(get("/api/audiences").session(vietnamese.session()))
                .andExpect(jsonPath("$[*].displayName").value(contains("Lập trình viên", "Người dùng cuối", "Vận hành")))
                .andExpect(jsonPath("$[2].templateBody", containsString("  - Lý do: {{.}}")))
                .andExpect(jsonPath("$[2].communicationIntent", containsString("operational risk")));
    }

    @Test
    void createsUpdatesAndPreviewsAnAudience() throws Exception {
        Admin admin = registerAndLogin("owner@example.com", "en");

        MvcResult created = create(admin, "  Leadership ", "Leaders", "Business impact only.", TEMPLATE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("leadership"))
                .andExpect(jsonPath("$.displayName").value("Leaders"))
                .andExpect(jsonPath("$.preset").value(false))
                .andReturn();
        UUID audienceId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));

        create(admin, "LEADERSHIP", "Again", "", TEMPLATE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("audience_code_taken"));
        create(admin, "9lives", "Cats", "", TEMPLATE)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors.code", containsString("starting with a letter")));
        create(admin, "board", "Board", "", "{{#whatChanged}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("template_invalid"));
        create(admin, "board", "Board", "", "{{narratives.operator}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("template_narratives_path"));

        mockMvc.perform(put("/api/audiences/{id}", audienceId)
                        .session(admin.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"renamed","displayName":"  Board  ","communicationIntent":"Money.",
                                 "templateBody":"* {{whatChanged}}"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("leadership"))
                .andExpect(jsonPath("$.displayName").value("Board"))
                .andExpect(jsonPath("$.communicationIntent").value("Money."))
                .andExpect(jsonPath("$.templateBody").value("* {{whatChanged}}"));

        mockMvc.perform(post("/api/audiences/preview")
                        .session(admin.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateBody\":\"## {{whatChanged}}\\n\\n<b>{{narrative}}</b>\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markdown", containsString("## A safer deployment workflow is now available.")))
                .andExpect(jsonPath("$.html", containsString("<h2>A safer deployment workflow is now available.</h2>")))
                .andExpect(jsonPath("$.html", containsString("&lt;b&gt;")));
        mockMvc.perform(post("/api/audiences/preview")
                        .session(admin.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateBody\":\"{{unknownVariable}}\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("template_invalid"));
    }

    @Test
    void limitsTheNumberOfAudiencesAndKeepsTheLastOne() throws Exception {
        Admin admin = registerAndLogin("owner@example.com", "en");
        for (int index = 4; index <= AudienceService.MAX_AUDIENCES; index++) {
            create(admin, "audience_" + index, "Audience " + index, "", TEMPLATE).andExpect(status().isCreated());
        }
        create(admin, "one_too_many", "Too many", "", TEMPLATE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("audience_limit"));

        String list = mockMvc.perform(get("/api/audiences").session(admin.session()))
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(list, "$[*].id");
        for (String id : ids.subList(1, ids.size())) {
            mockMvc.perform(delete("/api/audiences/{id}", id).session(admin.session()).with(csrf()))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(delete("/api/audiences/{id}", ids.getFirst()).session(admin.session()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("audience_last"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audience_definitions WHERE organization_id = ?", Integer.class, admin.organizationId()
        )).isEqualTo(1);
    }

    @Test
    void resetsOnlyShippedAudiencesInTheCurrentOutputLanguage() throws Exception {
        Admin admin = registerAndLogin("owner@example.com", "en");
        UUID operator = idOf(admin, "operator");
        mockMvc.perform(put("/api/audiences/{id}", operator)
                        .session(admin.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Ops\",\"communicationIntent\":\"\",\"templateBody\":\"{{whatChanged}}\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/organization/output-language")
                        .session(admin.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outputLanguage\":\"vi\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/audiences/{id}/reset-to-preset", operator).session(admin.session()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Vận hành"))
                .andExpect(jsonPath("$.communicationIntent", containsString("operational risk")))
                .andExpect(jsonPath("$.templateBody", containsString("  - Cách chuyển đổi: {{.}}")));

        UUID custom = UUID.fromString(JsonPath.read(create(admin, "board", "Board", "", TEMPLATE)
                .andReturn().getResponse().getContentAsString(), "$.id"));
        mockMvc.perform(post("/api/audiences/{id}/reset-to-preset", custom).session(admin.session()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("audience_not_preset"));
    }

    @Test
    void onlyAdministratorsOfTheOrganizationManageItsAudiences() throws Exception {
        Admin admin = registerAndLogin("owner@example.com", "en");
        Admin other = registerAndLogin("other@example.com", "en");
        UUID operator = idOf(admin, "operator");
        MockHttpSession member = member(admin.organizationId());

        mockMvc.perform(get("/api/audiences").session(member)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/audiences").session(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/audiences").session(member)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/audiences")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/audiences/{id}", operator).session(other.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("audience_not_found"));
        mockMvc.perform(put("/api/audiences/{id}", operator).session(other.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Taken\",\"templateBody\":\"{{whatChanged}}\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/audiences/{id}", operator).session(other.session()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/audiences/{id}/reset-to-preset", operator).session(other.session()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/audiences/{id}", operator).session(admin.session()))
                .andExpect(jsonPath("$.displayName").value("Operator"));
    }

    private ResultActions create(Admin admin, String code, String name, String intent, String template) throws Exception {
        return mockMvc.perform(post("/api/audiences")
                .session(admin.session())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Map.of(
                        "code", code,
                        "displayName", name,
                        "communicationIntent", intent,
                        "templateBody", template
                ))));
    }

    private UUID idOf(Admin admin, String code) throws Exception {
        String list = mockMvc.perform(get("/api/audiences").session(admin.session()))
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(list, "$[?(@.code == '" + code + "')].id");
        return UUID.fromString(ids.getFirst());
    }

    private MockHttpSession member(UUID organizationId) throws Exception {
        jdbcTemplate.update(
                """
                        INSERT INTO app_users (id, organization_id, email, password_hash, display_name, role, created_at)
                        VALUES (?, ?, 'member@example.com', ?, 'Member', 'MEMBER', now())
                        """,
                UUID.randomUUID(),
                organizationId,
                passwordEncoder.encode("member-password")
        );
        return login("member@example.com", "member-password");
    }

    private Admin registerAndLogin(String email, String language) throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName(email);
        request.setDisplayName("Mai Tran");
        request.setEmail(email);
        request.setPassword("owner-password");
        request.setOutputLanguage(language);
        RegistrationResult registration = registrationService.register(request);
        return new Admin(registration.organizationId(), login(email, "owner-password"));
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
