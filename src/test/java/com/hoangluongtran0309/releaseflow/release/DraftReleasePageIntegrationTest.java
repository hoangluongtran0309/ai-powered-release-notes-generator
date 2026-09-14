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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class DraftReleasePageIntegrationTest extends PostgreSqlIntegrationTest {

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
        jdbcTemplate.update("DELETE FROM change_processing_jobs");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM github_integrations");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void createsADraftAndManagesItsChangesFromThePage() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner);
        UUID feature = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat(ui): add the inbox", "FEATURE", false, false, null);
        UUID breaking = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 2,
                "fix!: rename config keys", "FIX", true, false, owner.userId());
        TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 3,
                "docs: explain releases", "DOCUMENTATION", false, false, null);
        TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 4,
                "Tidy the exporter", "UNKNOWN", false, true, null);

        mockMvc.perform(get("/releases").session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(view().name("releases"))
                .andExpect(content().string(matchesPattern(
                        "(?s).*class=\"app-nav-link is-active\"[^>]*aria-current=\"page\".*Releases.*")))
                .andExpect(content().string(containsString("Create a draft release")));

        MvcResult created = mockMvc.perform(post("/projects/{projectId}/releases", projectId)
                        .session(owner.session())
                        .with(csrf())
                        .param("version", " 1.4.0 ")
                        .param("summary", "Exports and a faster inbox."))
                .andExpect(status().isFound())
                .andReturn();
        String draftPath = created.getResponse().getRedirectedUrl();
        assertThat(draftPath).matches("/projects/" + projectId + "/releases/[0-9a-f-]{36}");

        mockMvc.perform(get(draftPath).session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(view().name("release-draft"))
                .andExpect(content().string(containsString("No changes yet.")))
                .andExpect(content().string(containsString("name=\"changeIds\" value=\"" + feature + "\"")))
                .andExpect(content().string(not(containsString("Tidy the exporter"))))
                .andExpect(content().string(containsString("Add all available")));

        mockMvc.perform(post(draftPath + "/changes")
                        .session(owner.session())
                        .with(csrf())
                        .param("changeIds", feature.toString(), breaking.toString()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(draftPath));
        mockMvc.perform(get(draftPath).session(owner.session()))
                .andExpect(content().string(matchesPattern(
                        "(?s).*id=\"included-" + feature + "\".*id=\"included-" + breaking + "\".*")))
                .andExpect(content().string(matchesPattern(
                        "(?s).*aria-label=\"Release note preview\".*Exports and a faster inbox\\..*"
                                + "<h3 class=\"font-bold\">Breaking changes</h3>.*rename config keys.*\\(Fix\\).*"
                                + "<h3 class=\"font-bold\">Features</h3>.*add the inbox.*")));

        mockMvc.perform(post(draftPath + "/changes")
                        .session(owner.session())
                        .with(csrf())
                        .param("allAvailable", "true"))
                .andExpect(redirectedUrl(draftPath));
        mockMvc.perform(post(draftPath + "/changes/" + breaking + "/remove")
                        .session(owner.session())
                        .with(csrf()))
                .andExpect(redirectedUrl(draftPath));
        mockMvc.perform(post(draftPath)
                        .session(owner.session())
                        .with(csrf())
                        .param("version", "1.5.0")
                        .param("summary", ""))
                .andExpect(redirectedUrl(draftPath));

        mockMvc.perform(get("/releases").session(owner.session()))
                .andExpect(content().string(containsString(">1.5.0</h2>")))
                .andExpect(content().string(containsString("2 changes")))
                .andExpect(content().string(containsString("href=\"" + draftPath + "\"")))
                .andExpect(content().string(not(containsString("Create a draft release"))));

        mockMvc.perform(post(draftPath + "/discard")
                        .session(owner.session())
                        .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/releases?project=" + projectId));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM release_changes", Long.class)).isZero();
        mockMvc.perform(get("/releases").session(owner.session()))
                .andExpect(content().string(containsString("Create a draft release")));
    }

    @Test
    void reportsValidationConflictsAndMissingDrafts() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        Owner other = registerAndLogin("other@example.com");
        UUID projectId = createProject(owner);

        mockMvc.perform(post("/projects/{projectId}/releases", projectId)
                        .session(owner.session())
                        .with(csrf())
                        .param("version", "  "))
                .andExpect(status().isOk())
                .andExpect(view().name("releases"))
                .andExpect(model().attributeHasFieldErrors("releaseRequest", "version"));

        mockMvc.perform(post("/projects/{projectId}/releases", projectId)
                        .session(owner.session())
                        .with(csrf())
                        .param("version", "1.0.0"))
                .andExpect(status().isFound());
        mockMvc.perform(post("/projects/{projectId}/releases", projectId)
                        .session(owner.session())
                        .with(csrf())
                        .param("version", "2.0.0"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("already has a draft release")));

        String draftPath = "/projects/" + projectId + "/releases/"
                + jdbcTemplate.queryForObject("SELECT id FROM releases", UUID.class);
        mockMvc.perform(post(draftPath + "/changes").session(owner.session()).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Choose changes to add")));
        mockMvc.perform(get(draftPath).session(other.session()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Release was not found.")));
        mockMvc.perform(get("/releases").session(other.session()).param("project", projectId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Project was not found.")))
                .andExpect(content().string(not(containsString("Create a draft release"))));
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

    private Owner registerAndLogin(String email) throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName(email);
        request.setDisplayName(email);
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
