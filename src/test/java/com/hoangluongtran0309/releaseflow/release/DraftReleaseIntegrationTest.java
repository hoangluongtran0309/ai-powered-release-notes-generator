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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DraftReleaseIntegrationTest extends PostgreSqlIntegrationTest {

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
        jdbcTemplate.execute("TRUNCATE automation_action_runs, automation_runs, automation_publish_jobs, automation_rule_actions, automation_rules, release_audience_notes, release_change_reviews, release_notes, release_changes, releases");
        jdbcTemplate.update("DELETE FROM change_processing_jobs");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM integration_sources");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void createsEditsAndDiscardsSeveralDraftsOfAProject() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner);

        String created = createDraft(owner, projectId, "{\"version\":\" 1.4.0 \",\"summary\":\"  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value("1.4.0"))
                .andExpect(jsonPath("$.summary").doesNotExist())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.plannedReleaseAt").doesNotExist())
                .andExpect(jsonPath("$.changes.length()").value(0))
                .andExpect(jsonPath("$.decisions.length()").value(0))
                .andExpect(jsonPath("$.preview.length()").value(0))
                .andReturn().getResponse().getContentAsString();
        UUID releaseId = UUID.fromString(JsonPath.read(created, "$.id"));

        createDraft(owner, projectId, "{\"version\":\"2.0.0\",\"plannedReleaseAt\":\"2099-10-01T09:00:00+07:00\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.plannedReleaseAt").value("2099-10-01T02:00:00Z"));

        mockMvc.perform(put("/api/projects/{projectId}/releases/{releaseId}", projectId, releaseId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.5.0\",\"summary\":\"Exports and a faster inbox.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1.5.0"))
                .andExpect(jsonPath("$.summary").value("Exports and a faster inbox."));
        mockMvc.perform(get("/api/projects/{projectId}/releases", projectId).session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].version").value(containsInAnyOrder("1.5.0", "2.0.0")))
                .andExpect(jsonPath("$[*].status").value(contains("DRAFT", "DRAFT")))
                .andExpect(jsonPath("$[*].changeCount").value(contains(0, 0)))
                .andExpect(jsonPath("$[*].reviewedCount").value(contains(0, 0)));

        mockMvc.perform(delete("/api/projects/{projectId}/releases/{releaseId}", projectId, releaseId)
                        .session(owner.session())
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}", projectId, releaseId)
                        .session(owner.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("release_not_found"));
        mockMvc.perform(get("/api/projects/{projectId}/releases", projectId).session(owner.session()))
                .andExpect(jsonPath("$[*].version").value(contains("2.0.0")));
    }

    @Test
    void validatesReleaseDetails() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner);

        createDraft(owner, projectId, "{\"version\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors.version").exists());
        createDraft(owner, projectId, "{\"version\":\"" + "9".repeat(51) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.version").exists());
        createDraft(owner, projectId, "{\"version\":\"1.0.0\",\"summary\":\"" + "s".repeat(2001) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.summary").exists());
        createDraft(owner, projectId, "{\"version\":\"1.0.0\",\"plannedReleaseAt\":\"next Tuesday\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("invalid_release_schedule"));
        createDraft(owner, projectId, "{\"version\":\"1.0.0\",\"plannedReleaseAt\":\"2020-01-01T00:00:00Z\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_release_schedule"))
                .andExpect(jsonPath("$.detail").value("The planned release time must be in the future."));
        mockMvc.perform(get("/api/projects/{projectId}/releases", projectId).session(owner.session()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void addsProcessedChangesOfTheProjectAndBuildsThePreview() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner);
        UUID otherProjectId = createProject(owner);
        UUID feature = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat(ui): add the inbox", "FEATURE", false, false, null);
        UUID breaking = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 2,
                "fix!: rename config keys", "FIX", true, false, owner.userId());
        UUID docs = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 3,
                "docs: explain releases", "DOCUMENTATION", false, false, null);
        UUID unsettled = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 4,
                "Tidy the exporter", "UNKNOWN", false, true, null);
        UUID elsewhere = TestChanges.insert(jdbcTemplate, owner.organizationId(), otherProjectId, 5,
                "feat: another project", "FEATURE", false, false, null);
        UUID releaseId = draftId(owner, projectId);

        // A change that still needs review may join a draft; the release's review settles it.
        available(owner, projectId, releaseId)
                .andExpect(jsonPath("$[*].pullRequestNumber").value(contains(1, 2, 3, 4)));

        addChanges(owner, projectId, releaseId, "{\"changeIds\":[\"%s\"]}".formatted(elsewhere))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("change_not_found"));
        addChanges(owner, projectId, releaseId, "{\"changeIds\":[\"%s\"],\"allAvailable\":true}".formatted(feature))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        addChanges(owner, projectId, releaseId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));

        addChanges(owner, projectId, releaseId, "{\"changeIds\":[\"%s\",\"%s\"]}".formatted(feature, unsettled))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes[*].pullRequestNumber").value(contains(1, 4)))
                .andExpect(jsonPath("$.changes[1].needsReview").value(true));
        addChanges(owner, projectId, releaseId, "{\"changeIds\":[\"%s\"]}".formatted(feature))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes.length()").value(2));
        available(owner, projectId, releaseId)
                .andExpect(jsonPath("$[*].pullRequestNumber").value(contains(2, 3)));

        UUID secondRelease = UUID.fromString(JsonPath.read(createDraft(owner, projectId, "{\"version\":\"1.5.0\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id"));
        addChanges(owner, projectId, secondRelease, "{\"changeIds\":[\"%s\"]}".formatted(feature))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("change_not_releasable"));

        addChanges(owner, projectId, releaseId, "{\"allAvailable\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes[*].id").value(containsInAnyOrder(
                        feature.toString(), breaking.toString(), docs.toString(), unsettled.toString())))
                .andExpect(jsonPath("$.preview[0].title").value("Breaking changes"))
                .andExpect(jsonPath("$.preview[0].items[0].title").value("rename config keys"))
                .andExpect(jsonPath("$.preview[0].items[0].category").value("FIX"))
                .andExpect(jsonPath("$.preview[1].title").value("Features"))
                .andExpect(jsonPath("$.preview[1].items[0].title").value("add the inbox"))
                .andExpect(jsonPath("$.preview[2].title").value("Documentation"))
                .andExpect(jsonPath("$.preview.length()").value(3));
        available(owner, projectId, releaseId).andExpect(jsonPath("$.length()").value(0));
        available(owner, projectId, secondRelease).andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(delete("/api/projects/{projectId}/releases/{releaseId}/changes/{changeId}",
                        projectId, releaseId, breaking)
                        .session(owner.session())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes[*].pullRequestNumber").value(contains(1, 3, 4)))
                .andExpect(jsonPath("$.preview[0].title").value("Features"));
        mockMvc.perform(delete("/api/projects/{projectId}/releases/{releaseId}/changes/{changeId}",
                        projectId, releaseId, elsewhere)
                        .session(owner.session())
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("change_not_found"));
        available(owner, projectId, releaseId)
                .andExpect(jsonPath("$[*].pullRequestNumber").value(contains(2)));
        mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}", projectId, releaseId).session(owner.session()))
                .andExpect(jsonPath("$.changes.length()").value(3));
    }

    @Test
    void keepsDraftsInsideTheirTenant() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        Owner other = registerAndLogin("other@example.com");
        UUID projectId = createProject(owner);
        UUID otherProjectId = createProject(other);
        UUID releaseId = draftId(owner, projectId);
        UUID change = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat: add the inbox", "FEATURE", false, false, null);

        mockMvc.perform(get("/api/projects/{projectId}/releases", projectId).session(other.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("project_not_found"));
        createDraft(other, projectId, "{\"version\":\"9.9.9\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("project_not_found"));
        mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}", projectId, releaseId)
                        .session(other.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("release_not_found"));
        mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}", otherProjectId, releaseId)
                        .session(other.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("release_not_found"));
        addChanges(other, projectId, releaseId, "{\"changeIds\":[\"%s\"]}".formatted(change))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("release_not_found"));
        String otherRelease = createDraft(other, otherProjectId, "{\"version\":\"1.0.0\"}")
                .andReturn().getResponse().getContentAsString();
        addChanges(other, otherProjectId, UUID.fromString(JsonPath.read(otherRelease, "$.id")),
                "{\"changeIds\":[\"%s\"]}".formatted(change))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("change_not_found"));

        mockMvc.perform(post("/api/projects/{projectId}/releases", projectId)
                        .session(owner.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.0.0\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/projects/{projectId}/releases", projectId))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions createDraft(Owner owner, UUID projectId, String body) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/releases", projectId)
                .session(owner.session())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private UUID draftId(Owner owner, UUID projectId) throws Exception {
        String body = createDraft(owner, projectId, "{\"version\":\"1.4.0\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private ResultActions addChanges(Owner owner, UUID projectId, UUID releaseId, String body) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/changes", projectId, releaseId)
                .session(owner.session())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions available(Owner owner, UUID projectId, UUID releaseId) throws Exception {
        return mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}/available-changes", projectId, releaseId)
                        .session(owner.session()))
                .andExpect(status().isOk());
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
