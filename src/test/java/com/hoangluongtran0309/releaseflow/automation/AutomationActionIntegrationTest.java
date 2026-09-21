package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.support.ConfluenceStub;
import com.hoangluongtran0309.releaseflow.support.NotionStub;
import com.hoangluongtran0309.releaseflow.support.TeamsStub;
import com.hoangluongtran0309.releaseflow.support.ZendeskStub;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

    @Test
    void filesTheNoteAsAChildPageInNotion() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID runId = notionRun(owner, projectId, "1.4.0");

        work();

        mockMvc.perform(get("/api/automation/runs/{runId}", runId).session(owner.session()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.actions[0].externalReference").value(NotionStub.PAGE_URL));
        NotionStub.RecordedRequest request = NOTION.requests().getFirst();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/v1/pages");
        assertThat(request.authorization()).isEqualTo("Bearer notion-integration-token");
        assertThat(request.notionVersion()).isEqualTo("2026-03-11");
        // The page id is sent the way Notion writes one, whichever way it was pasted in.
        assertThat(request.body()).contains("1a2b3c4d-5e6f-4a5b-8c9d-0e1f2a3b4c5d");
        assertThat(request.body()).contains("Release 1.4.0");
    }

    @Test
    void leavesANotionPageItCouldNotConfirmUnknownAndFailsOneNotionRefused() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID unconfirmed = notionRun(owner, projectId, "1.4.0");
        NOTION.respondWith(503);
        work();
        mockMvc.perform(get("/api/automation/runs/{runId}", unconfirmed).session(owner.session()))
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("execution_outcome_unknown"));

        NOTION.respondWith(400);
        UUID refused = notionRun(owner, projectId, "1.5.0");
        work();
        mockMvc.perform(get("/api/automation/runs/{runId}", refused).session(owner.session()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("notion_rejected"));
    }

    @Test
    void refusesToFollowARedirectAwayFromNotion() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID runId = notionRun(owner, projectId, "1.4.0");
        NOTION.redirectTo("http://127.0.0.1:9/v1/pages");

        work();

        // A 302 nobody follows is an answer Notion gave, and not a success.
        mockMvc.perform(get("/api/automation/runs/{runId}", runId).session(owner.session()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("notion_rejected"));
    }

    @Test
    void createsAStoragePageInConfluenceAndEscapesRawHtml() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID runId = confluenceRun(owner, projectId, "1.4.0");

        work();

        mockMvc.perform(get("/api/automation/runs/{runId}", runId).session(owner.session()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.actions[0].externalReference")
                        .value(ConfluenceStub.SITE + "/wiki/spaces/42/pages/" + ConfluenceStub.PAGE_ID));
        ConfluenceStub.RecordedRequest request = CONFLUENCE.requests().getFirst();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/wiki/api/v2/pages");
        assertThat(request.authorization()).isEqualTo("Basic " + Base64.getEncoder().encodeToString(
                (CONFLUENCE_ACCOUNT + ":confluence-api-token").getBytes(StandardCharsets.UTF_8)));
        assertThat(request.body()).contains("\"representation\":\"storage\"");
        assertThat(request.body()).contains("Release 1.4.0");
        assertThat(request.body()).contains("releaseflow-action:");
    }

    @Test
    void leavesAConfluencePageItCouldNotConfirmUnknownAndFailsOneConfluenceRefused() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID unconfirmed = confluenceRun(owner, projectId, "1.4.0");
        CONFLUENCE.respondWith(502);
        work();
        mockMvc.perform(get("/api/automation/runs/{runId}", unconfirmed).session(owner.session()))
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("execution_outcome_unknown"));

        CONFLUENCE.respondWith(403);
        UUID refused = confluenceRun(owner, projectId, "1.5.0");
        work();
        mockMvc.perform(get("/api/automation/runs/{runId}", refused).session(owner.session()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("confluence_rejected"));
    }

    @Test
    void refusesToFollowARedirectAwayFromConfluence() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID runId = confluenceRun(owner, projectId, "1.4.0");
        CONFLUENCE.redirectTo("http://127.0.0.1:9/wiki/api/v2/pages");

        work();

        mockMvc.perform(get("/api/automation/runs/{runId}", runId).session(owner.session()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("confluence_rejected"));
    }

    @Test
    void postsTheNoteThroughAWorkflowsCallback() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID runId = teamsRun(owner, projectId, "1.4.0");

        work();

        mockMvc.perform(get("/api/automation/runs/{runId}", runId).session(owner.session()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                // Teams names nothing to link to, so a delivery records no reference.
                .andExpect(jsonPath("$.actions[0].externalReference").doesNotExist());
        TeamsStub.RecordedRequest request = TEAMS.requests().getFirst();
        assertThat(request.method()).isEqualTo("POST");
        // The stand-in changes the origin only; the path and the signature are the flow's.
        assertThat(request.path())
                .isEqualTo("/powerautomate/automations/direct/workflows/2f1a6c/triggers/manual/paths/invoke");
        assertThat(request.query()).contains("sig=uT8k_signature-value");
        assertThat(request.body()).contains("Release 1.4.0");
    }

    @Test
    void leavesATeamsMessageItCouldNotConfirmUnknownAndFailsOneTeamsRefused() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID unconfirmed = teamsRun(owner, projectId, "1.4.0");
        TEAMS.respondWith(503);
        work();
        mockMvc.perform(get("/api/automation/runs/{runId}", unconfirmed).session(owner.session()))
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("execution_outcome_unknown"));

        TEAMS.respondWith(400);
        UUID refused = teamsRun(owner, projectId, "1.5.0");
        work();
        mockMvc.perform(get("/api/automation/runs/{runId}", refused).session(owner.session()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("teams_rejected"));
    }

    @Test
    void refusesToFollowARedirectAwayFromTeams() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID runId = teamsRun(owner, projectId, "1.4.0");
        // The callback URL is a credential; following this would hand it to another origin.
        TEAMS.redirectTo("http://127.0.0.1:9/collect");

        work();

        mockMvc.perform(get("/api/automation/runs/{runId}", runId).session(owner.session()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("teams_rejected"));
    }

    @Test
    void publishesTheNoteAsAHelpCentreArticleAndEscapesRawHtml() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID runId = zendeskRun(owner, projectId, "1.4.0");

        work();

        mockMvc.perform(get("/api/automation/runs/{runId}", runId).session(owner.session()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.actions[0].externalReference").value(ZendeskStub.ARTICLE_URL));
        ZendeskStub.RecordedRequest token = ZENDESK.requests().getFirst();
        assertThat(token.path()).isEqualTo("/oauth/tokens");
        assertThat(token.contentType()).startsWith("application/x-www-form-urlencoded");
        assertThat(token.body()).contains("grant_type=client_credentials", "scope=write", "client_id=releaseflow");
        ZendeskStub.RecordedRequest article = ZENDESK.articleRequests().getFirst();
        assertThat(article.path())
                .isEqualTo("/api/v2/help_center/sections/" + ZendeskStub.SECTION_ID + "/articles.json");
        assertThat(article.authorization()).isEqualTo("Bearer " + ZendeskStub.ACCESS_TOKEN);
        assertThat(article.body()).contains("\"locale\":\"en\"", "\"draft\":false", "\"notify_subscribers\":false");
        assertThat(article.body()).contains("Release 1.4.0");
        // An article nobody restricted is left open.
        assertThat(article.body()).doesNotContain("user_segment_id");
    }

    @Test
    void neverPublishesWhenZendeskWouldNotIssueAToken() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);

        // Nothing is published until the second call, so every way the first can fail is
        // FAILED: repeating it cannot duplicate an article that never existed.
        UUID refused = zendeskRun(owner, projectId, "1.4.0");
        ZENDESK.failTokenWith(400);
        work();
        mockMvc.perform(get("/api/automation/runs/{runId}", refused).session(owner.session()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("zendesk_auth_rejected"));

        ZENDESK.reset();
        ZENDESK.failTokenWith(500);
        UUID unavailable = zendeskRun(owner, projectId, "1.5.0");
        work();
        mockMvc.perform(get("/api/automation/runs/{runId}", unavailable).session(owner.session()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("zendesk_auth_unavailable"));

        ZENDESK.reset();
        ZENDESK.answerTokenWithoutToken();
        UUID tokenless = zendeskRun(owner, projectId, "1.6.0");
        work();
        mockMvc.perform(get("/api/automation/runs/{runId}", tokenless).session(owner.session()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("zendesk_auth_unavailable"));
        assertThat(ZENDESK.articleRequests()).isEmpty();
    }

    @Test
    void leavesAZendeskArticleItCouldNotConfirmUnknownAndFailsOneZendeskRefused() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);

        UUID unconfirmed = zendeskRun(owner, projectId, "1.4.0");
        ZENDESK.failArticleWith(502);
        work();
        mockMvc.perform(get("/api/automation/runs/{runId}", unconfirmed).session(owner.session()))
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("execution_outcome_unknown"));

        // A rate limit is a decision, not an accident: nothing was written.
        ZENDESK.reset();
        ZENDESK.failArticleWith(429);
        UUID refused = zendeskRun(owner, projectId, "1.5.0");
        work();
        mockMvc.perform(get("/api/automation/runs/{runId}", refused).session(owner.session()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("zendesk_rejected"));

        // An answer that names no article leaves nobody able to say whether one exists.
        ZENDESK.reset();
        ZENDESK.answerArticleWithoutUrl();
        UUID nameless = zendeskRun(owner, projectId, "1.6.0");
        work();
        mockMvc.perform(get("/api/automation/runs/{runId}", nameless).session(owner.session()))
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("execution_outcome_unknown"));
    }

    @Test
    void restrictsAZendeskArticleToASegmentWhenOneIsNamed() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID ruleId = createdRuleId(createRule(owner, zendeskRule(
                "Zendesk restricted", "MANUAL", projectId, audienceId(owner, "end_user"),
                ZendeskStub.SUBDOMAIN, ZendeskStub.SECTION_ID, "99", "zendesk-client-secret"))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        UUID runId = run(owner, ruleId, publishedRelease(owner, projectId, "1.4.0"));

        work();

        mockMvc.perform(get("/api/automation/runs/{runId}", runId).session(owner.session()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        assertThat(ZENDESK.articleRequests().getFirst().body()).contains("\"user_segment_id\":99");
    }

    private UUID teamsRun(Owner owner, UUID projectId, String version) throws Exception {
        UUID ruleId = createdRuleId(createRule(owner,
                teamsRule("Teams " + version, "MANUAL", projectId, audienceId(owner, "end_user")))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        return run(owner, ruleId, publishedRelease(owner, projectId, version));
    }

    private UUID zendeskRun(Owner owner, UUID projectId, String version) throws Exception {
        UUID ruleId = createdRuleId(createRule(owner,
                zendeskRule("Zendesk " + version, "MANUAL", projectId, audienceId(owner, "end_user")))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        return run(owner, ruleId, publishedRelease(owner, projectId, version));
    }

    private UUID notionRun(Owner owner, UUID projectId, String version) throws Exception {
        UUID ruleId = createdRuleId(createRule(owner,
                notionRule("Notion " + version, "MANUAL", projectId, audienceId(owner, "end_user")))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        return run(owner, ruleId, publishedRelease(owner, projectId, version));
    }

    private UUID confluenceRun(Owner owner, UUID projectId, String version) throws Exception {
        UUID ruleId = createdRuleId(createRule(owner,
                confluenceRule("Confluence " + version, "MANUAL", projectId, audienceId(owner, "end_user")))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        return run(owner, ruleId, publishedRelease(owner, projectId, version));
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
