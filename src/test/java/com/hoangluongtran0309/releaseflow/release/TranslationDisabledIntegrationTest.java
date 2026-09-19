package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.hoangluongtran0309.releaseflow.support.TestChanges;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Release notes in a second language when no translation provider is configured. */
class TranslationDisabledIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

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
    void notesThatNeedTranslationFailAtOnceAndAreFixedByHand() throws Exception {
        Owner owner = registerAndLogin();
        mockMvc.perform(put("/api/organization/release-languages").session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetLanguages\":[\"en\",\"vi\"]}"))
                .andExpect(status().isOk());
        UUID projectId = createProject(owner);
        UUID change = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat: add export", "FEATURE", false, false, null);
        TestChanges.summarize(jdbcTemplate, change, "Tables export as CSV.", "{}");
        UUID untranslated = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 2,
                "feat: add import", "FEATURE", false, false, null);
        UUID releaseId = createDraft(owner, projectId);

        action(owner, projectId, releaseId, "request-review").andExpect(status().isOk());
        for (UUID id : List.of(change, untranslated)) {
            mockMvc.perform(put(releaseApi(projectId, releaseId) + "/changes/{changeId}/decision", id)
                            .session(owner.session()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"APPROVE\",\"category\":\"feature\",\"breaking\":false}"))
                    .andExpect(status().isOk());
        }
        String approved = action(owner, projectId, releaseId, "approve")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes.length()").value(6))
                .andExpect(jsonPath("$.notes[?(@.language == 'en')].translationStatus").value(everyItem(is("READY"))))
                .andExpect(jsonPath("$.notes[?(@.language == 'vi')].translationStatus").value(everyItem(is("FAILED"))))
                .andReturn().getResponse().getContentAsString();
        assertThat(jdbcTemplate.queryForObject("SELECT last_error FROM translation_jobs", String.class))
                .isEqualTo("translation_disabled");
        action(owner, projectId, releaseId, "translations/retry")
                .andExpect(jsonPath("$.notes[?(@.language == 'vi')].translationStatus").value(everyItem(is("FAILED"))));
        action(owner, projectId, releaseId, "publish")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("translations_not_ready"));

        List<String> failed = JsonPath.read(approved, "$.notes[?(@.translationStatus == 'FAILED')].id");
        for (String noteId : failed) {
            mockMvc.perform(put(releaseApi(projectId, releaseId) + "/notes/{noteId}", noteId)
                            .session(owner.session()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"# Bản phát hành 1.4.0\\n\"}"))
                    .andExpect(status().isOk());
        }
        action(owner, projectId, releaseId, "publish").andExpect(status().isOk());
    }

    private ResultActions action(Owner owner, UUID projectId, UUID releaseId, String action) throws Exception {
        return mockMvc.perform(post(releaseApi(projectId, releaseId) + "/" + action).session(owner.session()).with(csrf()));
    }

    private UUID createDraft(Owner owner, UUID projectId) throws Exception {
        String body = mockMvc.perform(post("/api/projects/{projectId}/releases", projectId)
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"1.4.0\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID releaseId = UUID.fromString(JsonPath.read(body, "$.id"));
        mockMvc.perform(post(releaseApi(projectId, releaseId) + "/changes")
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allAvailable\":true}"))
                .andExpect(status().isOk());
        return releaseId;
    }

    private UUID createProject(Owner owner) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects").session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ReleaseFlow\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private static String releaseApi(UUID projectId, UUID releaseId) {
        return "/api/projects/" + projectId + "/releases/" + releaseId;
    }

    private Owner registerAndLogin() throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName("Acme");
        request.setDisplayName("Mai Tran");
        request.setEmail("owner@example.com");
        request.setPassword("owner-password");
        RegistrationResult registration = registrationService.register(request);
        MvcResult login = mockMvc.perform(post("/login").with(csrf())
                        .param("email", "owner@example.com").param("password", "owner-password"))
                .andExpect(status().isFound())
                .andReturn();
        return new Owner(registration.organizationId(), (MockHttpSession) login.getRequest().getSession(false));
    }

    private record Owner(UUID organizationId, MockHttpSession session) {
    }
}
