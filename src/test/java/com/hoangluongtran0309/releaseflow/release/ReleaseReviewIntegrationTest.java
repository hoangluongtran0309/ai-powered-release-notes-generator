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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReleaseReviewIntegrationTest extends PostgreSqlIntegrationTest {

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
        jdbcTemplate.execute("TRUNCATE automation_action_runs, automation_runs, automation_publish_jobs, release_audience_notes, release_change_reviews, release_notes, release_changes, releases");
        jdbcTemplate.update("DELETE FROM change_processing_jobs");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM integration_sources");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void approvesAReleaseOnceEveryChangeHasADecision() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID feature = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat: add the inbox", "FEATURE", false, false, null);
        UUID unknown = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 2,
                "Tidy the exporter", "UNKNOWN", false, true, null);
        UUID breaking = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 3,
                "fix!: rename config keys", "FIX", true, true, null);
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0");

        action(owner, projectId, releaseId, "approve")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_status_conflict"));
        decide(owner, projectId, releaseId, feature, "APPROVE", "feature", false, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_status_conflict"));

        action(owner, projectId, releaseId, "request-review")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.reviewedCount").value(0));

        mockMvc.perform(put("/api/projects/{projectId}/releases/{releaseId}", projectId, releaseId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.4.1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_status_conflict"));
        mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/changes", projectId, releaseId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allAvailable\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_status_conflict"));
        action(owner, projectId, releaseId, "request-review")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_status_conflict"));
        action(owner, projectId, releaseId, "approve")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_review_incomplete"));

        // Approving confirms what the reviewer saw, so a different classification is stale.
        decide(owner, projectId, releaseId, feature, "APPROVE", "fix", false, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("classification_changed"));
        decide(owner, projectId, releaseId, unknown, "APPROVE", "unknown", false, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_change_review"));
        decide(owner, projectId, releaseId, unknown, "EDIT", "unknown", false, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_change_review"));
        mockMvc.perform(put("/api/projects/{projectId}/releases/{releaseId}/changes/{changeId}/decision",
                        projectId, releaseId, feature)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\",\"category\":\"feature\",\"breaking\":false,\"note\":\"" + "n".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.note").exists());
        mockMvc.perform(put("/api/projects/{projectId}/releases/{releaseId}/changes/{changeId}/decision",
                        projectId, releaseId, feature)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"feature\",\"breaking\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.action").exists());

        decide(owner, projectId, releaseId, unknown, "EDIT", "maintenance", false, "  Tooling only.  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewedCount").value(1))
                .andExpect(jsonPath("$.decisions[0].changeId").value(unknown.toString()))
                .andExpect(jsonPath("$.decisions[0].action").value("EDIT"))
                .andExpect(jsonPath("$.decisions[0].reviewerName").value("Mai Tran"))
                .andExpect(jsonPath("$.decisions[0].note").value("Tooling only."))
                .andExpect(jsonPath("$.changes[1].category").value("MAINTENANCE"))
                .andExpect(jsonPath("$.changes[1].needsReview").value(false));
        decide(owner, projectId, releaseId, feature, "APPROVE", "feature", false, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewedCount").value(2));
        action(owner, projectId, releaseId, "approve")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_review_incomplete"));
        decide(owner, projectId, releaseId, breaking, "APPROVE", "fix", true, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewedCount").value(3));
        // A later decision on the same change replaces the earlier one.
        decide(owner, projectId, releaseId, feature, "EDIT", "feature", false, "Confirmed with support.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewedCount").value(3));

        // Each decision reviews the change itself, as the Change Inbox does.
        Map<String, Object> edited = jdbcTemplate.queryForMap(
                "SELECT category, classification_source, needs_review, reviewed_by, reviewer_name FROM changes WHERE id = ?",
                unknown);
        assertThat(edited).containsEntry("category", "MAINTENANCE")
                .containsEntry("classification_source", "HUMAN")
                .containsEntry("needs_review", false)
                .containsEntry("reviewed_by", owner.userId())
                .containsEntry("reviewer_name", "Mai Tran");
        assertThat(jdbcTemplate.queryForMap("SELECT classification_source, reviewed_by FROM changes WHERE id = ?", feature))
                .containsEntry("classification_source", "RULES")
                .containsEntry("reviewed_by", owner.userId());
        mockMvc.perform(get("/api/projects/{projectId}/changes", projectId).param("status", "reviewed")
                        .session(owner.session()))
                .andExpect(jsonPath("$[*].pullRequestNumber").value(containsInAnyOrder(1, 2, 3)));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT action FROM release_change_reviews WHERE change_id = ?", String.class, feature)).isEqualTo("EDIT");

        action(owner, projectId, releaseId, "approve")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approverName").value("Mai Tran"))
                .andExpect(jsonPath("$.approvedAt", notNullValue()));

        decide(owner, projectId, releaseId, feature, "APPROVE", "feature", false, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_status_conflict"));
        mockMvc.perform(delete("/api/projects/{projectId}/releases/{releaseId}/changes/{changeId}", projectId, releaseId, feature)
                        .session(owner.session())
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_status_conflict"));
        action(owner, projectId, releaseId, "approve")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_status_conflict"));
        mockMvc.perform(get("/api/projects/{projectId}/releases", projectId).session(owner.session()))
                .andExpect(jsonPath("$[0].status").value("APPROVED"))
                .andExpect(jsonPath("$[0].changeCount").value(3))
                .andExpect(jsonPath("$[0].reviewedCount").value(3));
        mockMvc.perform(get("/api/projects/{projectId}/release-assignments", projectId).session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].changeId").value(containsInAnyOrder(
                        feature.toString(), unknown.toString(), breaking.toString())))
                .andExpect(jsonPath("$[0].releaseId").value(releaseId.toString()))
                .andExpect(jsonPath("$[0].version").value("1.4.0"))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    @Test
    void rejectsChangesDuringReviewAndReturnsToDraft() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID kept = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat: add the inbox", "FEATURE", false, false, null);
        UUID rejected = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 2,
                "fix: handle empty tables", "FIX", false, false, null);
        UUID empty = createDraft(owner, projectId, "0.9.0");
        action(owner, projectId, empty, "request-review")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_empty"));
        action(owner, projectId, empty, "return-to-draft")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_status_conflict"));

        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0");
        action(owner, projectId, releaseId, "request-review").andExpect(status().isOk());
        decide(owner, projectId, releaseId, rejected, "APPROVE", "fix", false, null).andExpect(status().isOk());
        decide(owner, projectId, releaseId, kept, "APPROVE", "feature", false, null).andExpect(status().isOk());

        // Rejecting removes the change and its decision; it can join another release.
        mockMvc.perform(delete("/api/projects/{projectId}/releases/{releaseId}/changes/{changeId}", projectId, releaseId, rejected)
                        .session(owner.session())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.changes[*].pullRequestNumber").value(contains(1)))
                .andExpect(jsonPath("$.reviewedCount").value(1));
        mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}/available-changes", projectId, empty)
                        .session(owner.session()))
                .andExpect(jsonPath("$[*].pullRequestNumber").value(contains(2)));

        action(owner, projectId, releaseId, "return-to-draft")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.decisions.length()").value(0));
        assertThat(jdbcTemplate.queryForObject("SELECT reviewed_by FROM changes WHERE id = ?", UUID.class, kept))
                .isEqualTo(owner.userId());

        action(owner, projectId, releaseId, "request-review").andExpect(status().isOk());
        decide(owner, projectId, releaseId, kept, "APPROVE", "feature", false, null).andExpect(status().isOk());
        action(owner, projectId, releaseId, "approve").andExpect(status().isOk());
        action(owner, projectId, releaseId, "return-to-draft")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.approvedAt").doesNotExist())
                .andExpect(jsonPath("$.approverName").doesNotExist())
                .andExpect(jsonPath("$.reviewedCount").value(0));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM release_change_reviews", Long.class)).isZero();
        mockMvc.perform(put("/api/projects/{projectId}/releases/{releaseId}", projectId, releaseId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.4.1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1.4.1"));
    }

    @Test
    void discardsReleasesInReviewOrApproved() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID inReviewChange = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat: add the inbox", "FEATURE", false, false, null);
        UUID inReview = draftWithAllChanges(owner, projectId, "1.4.0");
        action(owner, projectId, inReview, "request-review").andExpect(status().isOk());
        decide(owner, projectId, inReview, inReviewChange, "APPROVE", "feature", false, null).andExpect(status().isOk());
        TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 2,
                "fix: handle empty tables", "FIX", false, false, null);
        UUID approved = draftWithAllChanges(owner, projectId, "1.5.0");
        String body = action(owner, projectId, approved, "request-review")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID approvedChange = UUID.fromString(JsonPath.read(body, "$.changes[0].id"));
        decide(owner, projectId, approved, approvedChange, "APPROVE", "fix", false, null).andExpect(status().isOk());
        action(owner, projectId, approved, "approve").andExpect(status().isOk());

        for (UUID releaseId : new UUID[]{inReview, approved}) {
            mockMvc.perform(delete("/api/projects/{projectId}/releases/{releaseId}", projectId, releaseId)
                            .session(owner.session())
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM releases", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM release_changes", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM release_change_reviews", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM changes WHERE reviewed_by IS NOT NULL", Long.class))
                .isEqualTo(2);
        UUID next = createDraft(owner, projectId, "2.0.0");
        mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}/available-changes", projectId, next)
                        .session(owner.session()))
                .andExpect(jsonPath("$[*].pullRequestNumber").value(contains(1, 2)));
    }

    @Test
    void schedulesAReleaseUntilItIsPublished() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID change = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat: add the inbox", "FEATURE", false, false, null);
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0");

        schedule(owner, projectId, releaseId, "{\"plannedReleaseAt\":\"2099-10-01T09:00:00+02:00\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedReleaseAt").value("2099-10-01T07:00:00Z"));
        schedule(owner, projectId, releaseId, "{\"plannedReleaseAt\":\"2099-10-01T09:00\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedReleaseAt").value("2099-10-01T09:00:00Z"));
        schedule(owner, projectId, releaseId, "{\"plannedReleaseAt\":\"2020-10-01T09:00:00Z\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_release_schedule"));
        schedule(owner, projectId, releaseId, "{\"plannedReleaseAt\":\"soon\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_release_schedule"));
        mockMvc.perform(get("/api/projects/{projectId}/releases", projectId).session(owner.session()))
                .andExpect(jsonPath("$[0].plannedReleaseAt").value("2099-10-01T09:00:00Z"));

        action(owner, projectId, releaseId, "request-review").andExpect(status().isOk());
        decide(owner, projectId, releaseId, change, "APPROVE", "feature", false, null).andExpect(status().isOk());
        action(owner, projectId, releaseId, "approve").andExpect(status().isOk());
        schedule(owner, projectId, releaseId, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.plannedReleaseAt").doesNotExist());
        schedule(owner, projectId, releaseId, "{\"plannedReleaseAt\":\"2099-12-24T18:00:00Z\"}")
                .andExpect(status().isOk());

        action(owner, projectId, releaseId, "publish")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedReleaseAt").value("2099-12-24T18:00:00Z"));
        schedule(owner, projectId, releaseId, "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_published"));
    }

    @Test
    void keepsReviewInsideTheTenantAndProject() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        Owner other = registerAndLogin("other@example.com", "Other Owner");
        UUID projectId = createProject(owner);
        UUID otherProjectId = createProject(owner);
        UUID change = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat: add the inbox", "FEATURE", false, false, null);
        UUID elsewhere = TestChanges.insert(jdbcTemplate, owner.organizationId(), otherProjectId, 2,
                "feat: another project", "FEATURE", false, false, null);
        UUID unassigned = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 3,
                "fix: later work", "FIX", false, false, null);
        UUID releaseId = createDraft(owner, projectId, "1.4.0");
        mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/changes", projectId, releaseId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changeIds\":[\"%s\"]}".formatted(change)))
                .andExpect(status().isOk());
        action(owner, projectId, releaseId, "request-review").andExpect(status().isOk());

        for (String step : new String[]{"request-review", "approve", "return-to-draft", "publish"}) {
            action(other, projectId, releaseId, step)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("release_not_found"));
        }
        decide(other, projectId, releaseId, change, "APPROVE", "feature", false, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("release_not_found"));
        decide(owner, otherProjectId, releaseId, change, "APPROVE", "feature", false, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("release_not_found"));
        decide(owner, projectId, releaseId, elsewhere, "APPROVE", "feature", false, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("change_not_found"));
        decide(owner, projectId, releaseId, unassigned, "APPROVE", "fix", false, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("change_not_found"));
        schedule(other, projectId, releaseId, "{}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("release_not_found"));
        mockMvc.perform(get("/api/projects/{projectId}/release-assignments", projectId).session(other.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("project_not_found"));
        mockMvc.perform(get("/api/projects/{projectId}/release-assignments", otherProjectId).session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/approve", projectId, releaseId)
                        .session(owner.session()))
                .andExpect(status().isForbidden());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM release_change_reviews", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT reviewed_by FROM changes WHERE id = ?", UUID.class, change)).isNull();
    }

    private ResultActions action(Owner owner, UUID projectId, UUID releaseId, String action) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/" + action, projectId, releaseId)
                .session(owner.session())
                .with(csrf()));
    }

    private ResultActions decide(
            Owner owner,
            UUID projectId,
            UUID releaseId,
            UUID changeId,
            String action,
            String category,
            boolean breaking,
            String note
    ) throws Exception {
        String noteJson = note == null ? "" : ",\"note\":\"" + note + "\"";
        return mockMvc.perform(put("/api/projects/{projectId}/releases/{releaseId}/changes/{changeId}/decision",
                        projectId, releaseId, changeId)
                .session(owner.session())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"%s\",\"category\":\"%s\",\"breaking\":%s%s}".formatted(action, category, breaking, noteJson)));
    }

    private ResultActions schedule(Owner owner, UUID projectId, UUID releaseId, String body) throws Exception {
        return mockMvc.perform(put("/api/projects/{projectId}/releases/{releaseId}/schedule", projectId, releaseId)
                .session(owner.session())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private UUID createDraft(Owner owner, UUID projectId, String version) throws Exception {
        String body = mockMvc.perform(post("/api/projects/{projectId}/releases", projectId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"%s\"}".formatted(version)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private UUID draftWithAllChanges(Owner owner, UUID projectId, String version) throws Exception {
        UUID releaseId = createDraft(owner, projectId, version);
        mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/changes", projectId, releaseId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allAvailable\":true}"))
                .andExpect(status().isOk());
        return releaseId;
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
