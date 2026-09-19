package com.hoangluongtran0309.releaseflow.automation;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What each kind of delivery does against the provider standing in for it. */
class AutomationActionIntegrationTest extends AutomationIntegrationTestBase {

    @Autowired
    private AutomationWorker worker;

    @Test
    void publishesTheNoteAsAGitHubRelease() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        connectGitHubSource(owner, projectId, "releaseflow");
        UUID runId = gitHubRun(owner, projectId, "1.4.0");

        work();

        mockMvc.perform(get("/api/automation/runs/{runId}", runId).session(owner.session()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.actions[0].externalReference")
                        .value("https://github.com/acme/releaseflow/releases/tag/1.4.0"));
        assertThat(GITHUB.releaseBody("1.4.0")).contains("releaseflow-action:");
    }

    @Test
    void recognisesTheReleaseItAlreadyPublishedAndRefusesSomebodyElsesTag() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        connectGitHubSource(owner, projectId, "releaseflow");
        UUID runId = gitHubRun(owner, projectId, "1.4.0");
        work();
        String marked = GITHUB.releaseBody("1.4.0");

        // Sending the same action again finds its own release and is content.
        mockMvc.perform(post("/api/automation/runs/{runId}/retry", runId).session(owner.session()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("automation_run_not_retryable"));

        // A release for the tag that nobody here wrote is never overwritten.
        GITHUB.reset();
        GITHUB.publishedRelease("1.5.0", "Written by somebody else.");
        UUID conflicting = gitHubRun(owner, projectId, "1.5.0");
        work();
        mockMvc.perform(get("/api/automation/runs/{runId}", conflicting).session(owner.session()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("github_release_conflict"));
        assertThat(GITHUB.releaseBody("1.5.0")).isEqualTo("Written by somebody else.");
        assertThat(marked).contains("releaseflow-action:");
    }

    @Test
    void leavesAGitHubReleaseItCouldNotConfirmUnknown() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        connectGitHubSource(owner, projectId, "releaseflow");
        UUID runId = gitHubRun(owner, projectId, "1.4.0");
        GITHUB.failReleaseCreation(500);

        work();

        mockMvc.perform(get("/api/automation/runs/{runId}", runId).session(owner.session()))
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("execution_outcome_unknown"));
    }

    @Test
    void failsAGitHubReleaseTheTokenMayNotPublish() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        connectGitHubSource(owner, projectId, "releaseflow");
        UUID runId = gitHubRun(owner, projectId, "1.4.0");
        GITHUB.failReleaseCreation(403);

        work();

        mockMvc.perform(get("/api/automation/runs/{runId}", runId).session(owner.session()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("github_rejected"));
    }

    @Test
    void failsAGitHubReleaseWhoseRepositoryIsNoLongerTheProjectsOnlyOne() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        connectGitHubSource(owner, projectId, "releaseflow");
        UUID runId = gitHubRun(owner, projectId, "1.4.0");
        // A second source between the run and the delivery leaves no single repository.
        connectGitHubSource(owner, projectId, "releaseflow-docs");

        work();

        mockMvc.perform(get("/api/automation/runs/{runId}", runId).session(owner.session()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("github_source_missing"));
        assertThat(GITHUB.releaseBody("1.4.0")).isNull();
    }

    @Test
    void postsTheNoteToSlackAndReportsWhatSlackRefuses() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID ruleId = createdRuleId(createRule(owner, slackRule("Announce", "MANUAL", projectId, endUser, "en"))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());

        UUID delivered = run(owner, ruleId, publishedRelease(owner, projectId, "1.4.0"));
        work();
        assertThat(SLACK.messages()).hasSize(1);
        mockMvc.perform(get("/api/automation/runs/{runId}", delivered).session(owner.session()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        SLACK.respondWith(500);
        UUID refused = run(owner, ruleId, publishedRelease(owner, projectId, "1.5.0"));
        work();
        mockMvc.perform(get("/api/automation/runs/{runId}", refused).session(owner.session()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("slack_rejected"));
    }

    @Test
    void sendsTheNoteToTheAddressesAnEmailActionNames() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID ruleId = createdRuleId(createRule(owner, """
                {"name":"Mail it","triggerType":"MANUAL","projectId":"%s","actions":[
                  {"actionType":"EMAIL","audienceId":"%s","language":"en",
                   "recipients":"ops@example.com, support@example.com"}]}
                """.formatted(projectId, endUser)).andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        UUID runId = run(owner, ruleId, publishedRelease(owner, projectId, "1.4.0"));

        work();

        mockMvc.perform(get("/api/automation/runs/{runId}", runId).session(owner.session()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        assertThat(SMTP.messages()).hasSize(1);
        assertThat(SMTP.messages().getFirst().recipients())
                .containsExactly("ops@example.com", "support@example.com");
        assertThat(SMTP.messages().getFirst().body()).contains("Release 1.4.0");
    }

    @Test
    void reportsWhatTheMailServerRefuses() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID ruleId = createdRuleId(createRule(owner, """
                {"name":"Mail it","triggerType":"MANUAL","projectId":"%s","actions":[
                  {"actionType":"EMAIL","audienceId":"%s","language":"en","recipients":"ops@example.com"}]}
                """.formatted(projectId, endUser)).andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        UUID runId = run(owner, ruleId, publishedRelease(owner, projectId, "1.4.0"));
        SMTP.refuse(true);

        work();

        // SMTP may have taken the message for some recipients, so nobody repeats it alone.
        mockMvc.perform(get("/api/automation/runs/{runId}", runId).session(owner.session()))
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("execution_outcome_unknown"));
        assertThat(SMTP.messages()).isEmpty();
    }

    @Test
    void refusesAnEmailAddressNobodyCouldWriteTo() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");

        createRule(owner, """
                {"name":"Mail it","triggerType":"MANUAL","projectId":"%s","actions":[
                  {"actionType":"EMAIL","audienceId":"%s","language":"en","recipients":"not-an-address"}]}
                """.formatted(projectId, endUser))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("automation_email_recipient_invalid"));
    }

    private UUID gitHubRun(Owner owner, UUID projectId, String version) throws Exception {
        UUID endUser = audienceId(owner, "end_user");
        UUID ruleId = createdRuleId(createRule(owner, """
                {"name":"Publish %s","triggerType":"MANUAL","projectId":"%s","actions":[
                  {"actionType":"GITHUB_RELEASE","audienceId":"%s","language":"en"}]}
                """.formatted(version, projectId, endUser)).andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        return run(owner, ruleId, publishedRelease(owner, projectId, version));
    }

    // Works everything waiting, which is one outbox row and then the delivery.
    private void work() {
        while (worker.processOne()) {
            // Keep taking work until nothing is left.
        }
    }

    private UUID run(Owner owner, UUID ruleId, UUID releaseId) throws Exception {
        String body = mockMvc.perform(post("/api/automation/rules/{ruleId}/execute", ruleId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"releaseId\":\"%s\",\"requestId\":\"%s\"}".formatted(releaseId, UUID.randomUUID())))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }
}
