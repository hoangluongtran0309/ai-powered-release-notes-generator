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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReleaseLanguageIntegrationTest extends PostgreSqlIntegrationTest {

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
        jdbcTemplate.execute("TRUNCATE automation_action_runs, automation_runs, automation_publish_jobs, release_audience_notes, release_change_reviews, release_notes, release_changes, releases");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM organization_translation_settings");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void releaseNotesAreWrittenInTheOutputLanguageUntilAdministratorsChooseMore() throws Exception {
        Admin admin = registerAndLogin("owner@example.com");

        mockMvc.perform(get("/api/organization/release-languages").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetLanguages").value(contains("en")))
                .andExpect(jsonPath("$.outputLanguage").value("en"))
                .andExpect(jsonPath("$.translationEnabled").value(false))
                .andExpect(jsonPath("$.updatedBy", nullValue()));
        mockMvc.perform(get("/api/audiences").session(admin.session()))
                .andExpect(jsonPath("$[0].templateVariants").isEmpty());

        replace(admin.session(), "[\" VI \", \"en\", \"vi\", \"\"]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetLanguages").value(contains("vi", "en")))
                .andExpect(jsonPath("$.updatedBy").value("Mai Tran"));

        // The shipped audiences get their shipped Vietnamese templates.
        String audiences = mockMvc.perform(get("/api/audiences").session(admin.session()))
                .andReturn().getResponse().getContentAsString();
        List<String> operatorVi = JsonPath.read(audiences, "$[?(@.code == 'operator')].templateVariants.vi");
        List<String> endUserVi = JsonPath.read(audiences, "$[?(@.code == 'end_user')].templateVariants.vi");
        assertThat(operatorVi.getFirst()).contains("  - Lý do: {{.}}");
        assertThat(endUserVi.getFirst()).startsWith("- **{{whatChanged}}**");
        List<Object> noEnglish = JsonPath.read(audiences, "$[?(@.code == 'operator')].templateVariants.en");
        assertThat(noEnglish).as("the main template already covers English").isEmpty();

        // An audience created later gets a copy of its main template.
        mockMvc.perform(post("/api/audiences").session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"leadership","displayName":"Leadership","communicationIntent":"",
                                 "templateBody":"- {{whatChanged}}\\n"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateVariants.vi").value("- {{whatChanged}}\n"));
    }

    @Test
    void rejectsInvalidLanguageLists() throws Exception {
        Admin admin = registerAndLogin("owner@example.com");

        for (String invalid : List.of("[]", "[\" \"]", "[\"en\",\"vi\",\"fr\",\"de\",\"ja\",\"ko\"]", "[\"not a tag\"]")) {
            replace(admin.session(), invalid)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("invalid_release_languages"));
        }
        mockMvc.perform(put("/api/organization/release-languages").session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        replace(admin.session(), "[\"en\",\"vi\",\"fr\",\"de\",\"ja\"]").andExpect(status().isOk());
    }

    @Test
    void onlyAdministratorsChooseLanguagesAndEditLanguageTemplates() throws Exception {
        Admin admin = registerAndLogin("owner@example.com");
        MockHttpSession member = member(admin.organizationId());

        mockMvc.perform(get("/api/organization/release-languages").session(member)).andExpect(status().isOk());
        replace(member, "[\"vi\"]").andExpect(status().isForbidden());
        mockMvc.perform(post("/audiences/release-languages").session(member).with(csrf()).param("targetLanguages", "vi"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/audiences/{id}/templates/vi", UUID.randomUUID()).session(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"templateBody\":\"- x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorsEditAndResetLanguageTemplates() throws Exception {
        Admin admin = registerAndLogin("owner@example.com");
        replace(admin.session(), "[\"en\",\"vi\"]").andExpect(status().isOk());
        String operator = idOf(admin, "operator");

        template(admin, operator, "vi", "- **{{whatChanged}}** — {{#narrative}}{{.}}{{/narrative}}\\n")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateVariants.vi").value("- **{{whatChanged}}** — {{#narrative}}{{.}}{{/narrative}}\n"));
        template(admin, operator, "fr", "- {{whatChanged}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("template_language_not_targeted"));
        template(admin, operator, "vi", "{{#whatChanged}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("template_invalid"));
        template(admin, UUID.randomUUID().toString(), "vi", "- {{whatChanged}}")
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/audiences/{id}/reset-to-preset", operator).session(admin.session()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateVariants.vi", containsString("  - Lý do: {{.}}")));
    }

    @Test
    void theAudiencesPageSetsLanguagesAndLanguageTemplates() throws Exception {
        Admin admin = registerAndLogin("owner@example.com");

        mockMvc.perform(post("/audiences/release-languages").session(admin.session()).with(csrf())
                        .param("targetLanguages", "en, vi"))
                .andExpect(redirectedUrl("/audiences?languagesSaved"));
        String operator = idOf(admin, "operator");
        mockMvc.perform(get("/audiences/{id}", operator).session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Release note languages")))
                .andExpect(content().string(containsString("value=\"en, vi\"")))
                .andExpect(content().string(containsString("No translation provider is configured")))
                .andExpect(content().string(containsString("Template (vi)")));

        mockMvc.perform(post("/audiences/{id}/templates/vi", operator).session(admin.session()).with(csrf())
                        .param("templateBody", "- **{{whatChanged}}**\r\n"))
                .andExpect(redirectedUrl("/audiences/" + operator + "?templateSaved#language-templates"));
        mockMvc.perform(post("/audiences/{id}/templates/vi", operator).session(admin.session()).with(csrf())
                        .param("templateBody", "{{#whatChanged}}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("{{#whatChanged}}</textarea>")));
        mockMvc.perform(post("/audiences/release-languages").session(admin.session()).with(csrf())
                        .param("targetLanguages", "en, vi, fr, de, ja, ko"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Choose between one and 5 release note languages.")))
                .andExpect(content().string(containsString("value=\"en, vi, fr, de, ja, ko\"")));
        mockMvc.perform(get("/audiences").session(admin.session()))
                .andExpect(content().string(not(containsString("Template (fr)"))));
    }

    private ResultActions replace(MockHttpSession session, String languages) throws Exception {
        return mockMvc.perform(put("/api/organization/release-languages").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetLanguages\":" + languages + "}"));
    }

    private ResultActions template(Admin admin, String audienceId, String language, String body) throws Exception {
        return mockMvc.perform(put("/api/audiences/{id}/templates/{language}", audienceId, language)
                .session(admin.session()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateBody\":\"" + body + "\"}"));
    }

    private String idOf(Admin admin, String code) throws Exception {
        List<String> ids = JsonPath.read(mockMvc.perform(get("/api/audiences").session(admin.session()))
                .andReturn().getResponse().getContentAsString(), "$[?(@.code == '" + code + "')].id");
        return ids.getFirst();
    }

    private MockHttpSession member(UUID organizationId) throws Exception {
        jdbcTemplate.update(
                """
                        INSERT INTO app_users (id, organization_id, email, password_hash, display_name, role, created_at)
                        VALUES (?, ?, 'member@example.com', ?, 'Member', 'MEMBER', now())
                        """,
                UUID.randomUUID(), organizationId, passwordEncoder.encode("member-password")
        );
        return login("member@example.com", "member-password");
    }

    private Admin registerAndLogin(String email) throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName(email);
        request.setDisplayName("Mai Tran");
        request.setEmail(email);
        request.setPassword("owner-password");
        RegistrationResult registration = registrationService.register(request);
        return new Admin(registration.organizationId(), login(email, "owner-password"));
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/login").with(csrf()).param("email", email).param("password", password))
                .andExpect(status().isFound())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private record Admin(UUID organizationId, MockHttpSession session) {
    }
}
