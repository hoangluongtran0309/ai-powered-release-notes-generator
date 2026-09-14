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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReleasePublicationIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String EXPECTED_MARKDOWN = """
            # 1.4.0

            Exports and clearer config.

            ## Breaking changes

            - rename config keys ([#2](https://github.com/acme/releaseflow/pull/2)) — Fix

            ## Features

            - add the inbox ([#1](https://github.com/acme/releaseflow/pull/1))
            """;

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
    void publishesAnImmutableSnapshotWithMarkdownAndPublisher() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID feature = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat(ui): add the inbox", "FEATURE", false, false, null);
        TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 2,
                "fix!: rename config keys", "FIX", true, false, owner.userId());
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0");

        publish(owner, projectId, releaseId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt", notNullValue()))
                .andExpect(jsonPath("$.publisherName").value("Mai Tran"))
                .andExpect(jsonPath("$.markdown").value(EXPECTED_MARKDOWN))
                .andExpect(jsonPath("$.preview[0].title").value("Breaking changes"))
                .andExpect(jsonPath("$.preview[1].items[0].title").value("add the inbox"));

        // A later correction of an included change must not alter the published note.
        mockMvc.perform(post("/api/projects/{projectId}/changes/{changeId}/review", projectId, feature)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"documentation\",\"breaking\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}", projectId, releaseId)
                        .session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markdown").value(EXPECTED_MARKDOWN))
                .andExpect(jsonPath("$.preview[1].title").value("Features"))
                .andExpect(jsonPath("$.preview.length()").value(2));
        mockMvc.perform(get("/api/projects/{projectId}/releases", projectId).session(owner.session()))
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$[0].publishedAt", notNullValue()))
                .andExpect(jsonPath("$[0].changeCount").value(2));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT markdown FROM release_notes WHERE release_id = ?", String.class, releaseId
        )).isEqualTo(EXPECTED_MARKDOWN);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT sections ->> 0 IS NOT NULL FROM release_notes WHERE release_id = ?", Boolean.class, releaseId
        )).isTrue();
    }

    @Test
    void rejectsEveryChangeToAPublishedRelease() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID included = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat: add the inbox", "FEATURE", false, false, null);
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0");
        publish(owner, projectId, releaseId).andExpect(status().isOk());
        UUID later = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 2,
                "fix: handle empty tables", "FIX", false, false, null);

        expectPublished(publish(owner, projectId, releaseId));
        expectPublished(mockMvc.perform(put("/api/projects/{projectId}/releases/{releaseId}", projectId, releaseId)
                .session(owner.session())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":\"1.4.1\"}")));
        expectPublished(mockMvc.perform(delete("/api/projects/{projectId}/releases/{releaseId}", projectId, releaseId)
                .session(owner.session())
                .with(csrf())));
        expectPublished(addChanges(owner, projectId, releaseId, "{\"changeIds\":[\"%s\"]}".formatted(later)));
        expectPublished(mockMvc.perform(delete("/api/projects/{projectId}/releases/{releaseId}/changes/{changeId}",
                        projectId, releaseId, included)
                .session(owner.session())
                .with(csrf())));

        mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}", projectId, releaseId)
                        .session(owner.session()))
                .andExpect(jsonPath("$.version").value("1.4.0"))
                .andExpect(jsonPath("$.changes.length()").value(1));
    }

    @Test
    void refusesEmptyDraftsAndReusedVersions() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID emptyDraft = createDraft(owner, projectId, "1.4.0");

        publish(owner, projectId, emptyDraft)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("release_empty"));

        TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1, "feat: add the inbox", "FEATURE", false, false, null);
        addChanges(owner, projectId, emptyDraft, "{\"allAvailable\":true}").andExpect(status().isOk());
        publish(owner, projectId, emptyDraft).andExpect(status().isOk());

        mockMvc.perform(post("/api/projects/{projectId}/releases", projectId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.4.0\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_version_taken"));
        UUID next = createDraft(owner, projectId, "1.5.0");
        mockMvc.perform(put("/api/projects/{projectId}/releases/{releaseId}", projectId, next)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.4.0\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_version_taken"));
        mockMvc.perform(get("/api/projects/{projectId}/releases", projectId).session(owner.session()))
                .andExpect(jsonPath("$[0].version").value("1.5.0"))
                .andExpect(jsonPath("$[0].status").value("DRAFT"))
                .andExpect(jsonPath("$[1].version").value("1.4.0"))
                .andExpect(jsonPath("$[1].status").value("PUBLISHED"));
    }

    @Test
    void publishesOnlyInsideTheTenant() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        Owner other = registerAndLogin("other@example.com", "Other Owner");
        UUID projectId = createProject(owner);
        TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1, "feat: add the inbox", "FEATURE", false, false, null);
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0");

        publish(other, projectId, releaseId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("release_not_found"));
        mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/publish", projectId, releaseId)
                        .session(owner.session()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}", projectId, releaseId)
                        .session(owner.session()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.markdown").doesNotExist());
    }

    private static void expectPublished(ResultActions result) throws Exception {
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_published"))
                .andExpect(jsonPath("$.detail", startsWith("This release is published.")));
    }

    private ResultActions publish(Owner owner, UUID projectId, UUID releaseId) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/publish", projectId, releaseId)
                .session(owner.session())
                .with(csrf()));
    }

    private UUID createDraft(Owner owner, UUID projectId, String version) throws Exception {
        String body = mockMvc.perform(post("/api/projects/{projectId}/releases", projectId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"%s\",\"summary\":\"Exports and clearer config.\"}".formatted(version)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private UUID draftWithAllChanges(Owner owner, UUID projectId, String version) throws Exception {
        UUID releaseId = createDraft(owner, projectId, version);
        addChanges(owner, projectId, releaseId, "{\"allAvailable\":true}").andExpect(status().isOk());
        return releaseId;
    }

    private ResultActions addChanges(Owner owner, UUID projectId, UUID releaseId, String body) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/changes", projectId, releaseId)
                .session(owner.session())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
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
