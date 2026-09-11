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

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class ReleasePublicationPageIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        // Published releases reject DELETE by design; TRUNCATE bypasses row triggers.
        jdbcTemplate.execute("TRUNCATE release_notes, release_changes, releases");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM github_integrations");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void publishesFromTheDraftPageAndShowsAReadOnlyReleaseNote() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        String draftPath = createDraft(owner, projectId);

        mockMvc.perform(get(draftPath).session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(view().name("release-draft"))
                .andExpect(content().string(containsString("Add at least one change before publishing.")))
                .andExpect(content().string(matchesPattern("(?s).*<button type=\"submit\" class=\"btn btn-primary btn-sm\" disabled=\"disabled\">Publish 1\\.4\\.0</button>.*")));
        mockMvc.perform(post(draftPath + "/publish").session(owner.session()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Add at least one change before publishing this release.")));

        TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat(ui): add the *inbox*", "FEATURE", false, false, null);
        mockMvc.perform(post(draftPath + "/changes").session(owner.session()).with(csrf()).param("allAvailable", "true"))
                .andExpect(redirectedUrl(draftPath));
        mockMvc.perform(post(draftPath + "/publish").session(owner.session()).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(draftPath));

        mockMvc.perform(get(draftPath).session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(view().name("release-note"))
                .andExpect(content().string(matchesPattern("(?s).*Published by <span class=\"font-semibold\">Mai Tran</span>.*")))
                .andExpect(content().string(containsString("<h3 class=\"font-bold\">Features</h3>")))
                .andExpect(content().string(containsString("add the *inbox*")))
                .andExpect(content().string(containsString("# 1.4.0\n\nExports and clearer config.\n\n## Features\n\n- add the \\*inbox\\*")))
                .andExpect(content().string(containsString("x-data=\"copyText()\"")))
                .andExpect(content().string(not(containsString("Save details"))))
                .andExpect(content().string(not(containsString("Discard this draft"))))
                .andExpect(content().string(not(containsString("Add all available"))));

        mockMvc.perform(post(draftPath).session(owner.session()).with(csrf()).param("version", "9.9.9"))
                .andExpect(status().isConflict())
                .andExpect(view().name("release-note"))
                .andExpect(content().string(containsString("This release is published.")));

        mockMvc.perform(get("/releases").session(owner.session()))
                .andExpect(content().string(containsString("Published releases")))
                .andExpect(content().string(containsString("href=\"" + draftPath + "\">1.4.0</a>")))
                .andExpect(content().string(containsString("Create a draft release")));
    }

    private String createDraft(Owner owner, UUID projectId) throws Exception {
        MvcResult created = mockMvc.perform(post("/projects/{projectId}/releases", projectId)
                        .session(owner.session())
                        .with(csrf())
                        .param("version", "1.4.0")
                        .param("summary", "Exports and clearer config."))
                .andExpect(status().isFound())
                .andReturn();
        return created.getResponse().getRedirectedUrl();
    }

    private UUID createProject(Owner owner) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ReleaseFlow\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private Owner registerAndLogin(String email, String displayName) throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName(email);
        request.setDisplayName(displayName);
        request.setEmail(email);
        request.setPassword("owner-password");
        RegistrationResult registration = registrationService.register(request);
        MvcResult login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", "owner-password"))
                .andExpect(status().isFound())
                .andReturn();
        return new Owner(
                registration.organizationId(),
                registration.userId(),
                (MockHttpSession) login.getRequest().getSession(false)
        );
    }

    private record Owner(UUID organizationId, UUID userId, MockHttpSession session) {
    }
}
