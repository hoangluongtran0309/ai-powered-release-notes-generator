package com.hoangluongtran0309.releaseflow.automation;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AutomationIntegrationTest extends AutomationIntegrationTestBase {

    @Test
    void administratorsWriteEnableDisableAndArchiveRules() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID operator = audienceId(owner, "operator");

        UUID ruleId = createdRuleId(createRule(owner, slackRule("Announce", "RELEASE_PUBLISHED", projectId, endUser, "en"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Announce"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.projectName").value("ReleaseFlow"))
                .andExpect(jsonPath("$.actions.length()").value(1))
                .andExpect(jsonPath("$.actions[0].position").value(0))
                .andExpect(jsonPath("$.actions[0].secretConfigured").value(true))
                // A secret is write-only: no response ever repeats it.
                .andExpect(content().string(not(containsString(slackWebhook())))));

        // A second action is appended, and the first keeps its identity and its secret.
        String twoActions = """
                {"name":"Announce","triggerType":"RELEASE_PUBLISHED","projectId":"%s","actions":[
                  {"id":"%s","actionType":"SLACK","audienceId":"%s","language":"en"},
                  {"actionType":"EMAIL","audienceId":"%s","language":"en","recipients":"ops@example.com"}]}
                """.formatted(projectId, firstActionId(owner, ruleId), endUser, operator);
        mockMvc.perform(put("/api/automation/rules/{ruleId}", ruleId)
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(twoActions))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions.length()").value(2))
                .andExpect(jsonPath("$.actions[0].actionType").value("SLACK"))
                .andExpect(jsonPath("$.actions[0].secretConfigured").value(true))
                .andExpect(jsonPath("$.actions[1].actionType").value("EMAIL"))
                .andExpect(jsonPath("$.actions[1].recipients").value("ops@example.com"))
                .andExpect(jsonPath("$.actions[1].secretConfigured").value(false));

        enable(owner, ruleId).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
        mockMvc.perform(post("/api/automation/rules/{ruleId}/disable", ruleId).session(owner.session()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(delete("/api/automation/rules/{ruleId}", ruleId).session(owner.session()).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/automation/rules").session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/automation/rules/{ruleId}", ruleId).session(owner.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("automation_rule_not_found"));
    }

    @Test
    void refusesTwoRulesWithOneNameWhateverTheCasing() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");

        createRule(owner, slackRule("Announce", "RELEASE_PUBLISHED", projectId, endUser, "en"))
                .andExpect(status().isCreated());
        createRule(owner, slackRule("announce", "RELEASE_PUBLISHED", projectId, endUser, "en"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("automation_rule_name_taken"));

        // An archived rule gives its name back.
        UUID ruleId = createdRuleId(createRule(owner, slackRule("Other", "RELEASE_PUBLISHED", projectId, endUser, "en"))
                .andExpect(status().isCreated()));
        mockMvc.perform(delete("/api/automation/rules/{ruleId}", ruleId).session(owner.session()).with(csrf()))
                .andExpect(status().isNoContent());
        createRule(owner, slackRule("Other", "RELEASE_PUBLISHED", projectId, endUser, "en"))
                .andExpect(status().isCreated());
    }

    @Test
    void refusesARuleNobodyCouldCarryOut() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        Owner other = registerAndLogin("other@example.com", "Other Owner");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");

        createRule(owner, """
                {"name":"No actions","triggerType":"MANUAL","projectId":null,"actions":[]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("automation_action_required"));
        createRule(owner, slackRule("Foreign project", "MANUAL", createProject(other), endUser, "en"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("automation_project_not_found"));
        createRule(owner, slackRule("Foreign audience", "MANUAL", projectId, audienceId(other, "end_user"), "en"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("automation_audience_not_found"));
        createRule(owner, slackRule("Unwritten language", "MANUAL", projectId, endUser, "de"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("automation_language_not_configured"));
        createRule(owner, """
                {"name":"Elsewhere","triggerType":"MANUAL","projectId":"%s","actions":[
                  {"actionType":"SLACK","audienceId":"%s","language":"en","secret":"https://example.com/services/x"}]}
                """.formatted(projectId, endUser))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("automation_slack_webhook_invalid"));
        createRule(owner, """
                {"name":"No recipients","triggerType":"MANUAL","projectId":"%s","actions":[
                  {"actionType":"EMAIL","audienceId":"%s","language":"en"}]}
                """.formatted(projectId, endUser))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("automation_email_recipients_required"));
        createRule(owner, """
                {"name":"Own token","triggerType":"MANUAL","projectId":"%s","actions":[
                  {"actionType":"GITHUB_RELEASE","audienceId":"%s","language":"en","secret":"ghp_x"}]}
                """.formatted(projectId, endUser))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("automation_secret_not_allowed"));
    }

    @Test
    void refusesEnablingAGitHubRuleWithoutOneRepositoryToPublishTo() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID ruleId = createdRuleId(createRule(owner, """
                {"name":"Publish","triggerType":"RELEASE_PUBLISHED","projectId":"%s","actions":[
                  {"actionType":"GITHUB_RELEASE","audienceId":"%s","language":"en"}]}
                """.formatted(projectId, endUser)).andExpect(status().isCreated()));

        enable(owner, ruleId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("automation_github_source_missing"));

        connectGitHubSource(owner, projectId, "releaseflow");
        enable(owner, ruleId).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));

        // A second GitHub source leaves no single answer, so enabling is refused again.
        mockMvc.perform(post("/api/automation/rules/{ruleId}/disable", ruleId).session(owner.session()).with(csrf()))
                .andExpect(status().isOk());
        connectGitHubSource(owner, projectId, "releaseflow-docs");
        enable(owner, ruleId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("automation_github_source_missing"));
    }

    @Test
    void runsARuleByHandOnceForEachRequest() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        UUID ruleId = createdRuleId(createRule(owner, slackRule("By hand", "MANUAL", projectId, endUser, "en"))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());

        UUID requestId = UUID.randomUUID();
        String first = execute(owner, ruleId, releaseId, requestId)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.releaseVersion").value("1.4.0"))
                .andExpect(jsonPath("$.initiatorName").value("Mai Tran"))
                .andExpect(jsonPath("$.actions.length()").value(1))
                .andReturn().getResponse().getContentAsString();

        // The same request is the same run, never a second delivery.
        execute(owner, ruleId, releaseId, requestId)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(JsonPath.<String>read(first, "$.id")));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM automation_runs", Integer.class)).isOne();

        execute(owner, ruleId, releaseId, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("automation_request_id_required"));
    }

    @Test
    void refusesRunningARuleTheReleaseDoesNotFit() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID other = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        UUID approved = approvedReleaseWithChange(owner, other, "2.0.0");

        UUID scopedElsewhere = createdRuleId(createRule(owner, slackRule("Elsewhere", "MANUAL", other, endUser, "en"))
                .andExpect(status().isCreated()));
        enable(owner, scopedElsewhere).andExpect(status().isOk());
        execute(owner, scopedElsewhere, releaseId, UUID.randomUUID())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("automation_rule_unavailable"));

        UUID published = createdRuleId(createRule(owner, slackRule("Published only", "MANUAL", null, endUser, "en"))
                .andExpect(status().isCreated()));
        enable(owner, published).andExpect(status().isOk());
        execute(owner, published, approved, UUID.randomUUID())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("automation_release_not_published"));

        // A rule still disabled never runs, even by hand.
        UUID disabled = createdRuleId(createRule(owner, slackRule("Off", "MANUAL", projectId, endUser, "en"))
                .andExpect(status().isCreated()));
        execute(owner, disabled, releaseId, UUID.randomUUID())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("automation_rule_unavailable"));
    }

    @Test
    void turningARuleOffCancelsTheRunsItHadNotFinished() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        UUID ruleId = createdRuleId(createRule(owner, slackRule("By hand", "MANUAL", projectId, endUser, "en"))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        String run = execute(owner, ruleId, releaseId, UUID.randomUUID())
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        UUID runId = UUID.fromString(JsonPath.read(run, "$.id"));

        mockMvc.perform(post("/api/automation/rules/{ruleId}/disable", ruleId).session(owner.session()).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/automation/runs/{runId}", runId).session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.actions[0].status").value("CANCELLED"));
    }

    @Test
    void readsTheRunHistoryOnePageAtATime() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID ruleId = createdRuleId(createRule(owner, slackRule("By hand", "MANUAL", projectId, endUser, "en"))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        for (int release = 1; release <= 3; release++) {
            execute(owner, ruleId, publishedRelease(owner, projectId, "1." + release + ".0"), UUID.randomUUID())
                    .andExpect(status().isAccepted());
        }

        mockMvc.perform(get("/api/automation/runs?page=0&size=2").session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].releaseVersion").value("1.3.0"));
        mockMvc.perform(get("/api/automation/runs?page=1&size=2").session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].releaseVersion").value("1.1.0"));
        mockMvc.perform(get("/api/automation/runs?page=-1").session(owner.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_automation_run_page"));
        mockMvc.perform(get("/api/automation/runs?size=101").session(owner.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_automation_run_page"));
    }

    @Test
    void onlyAdministratorsOfTheOrganizationSeeAutomation() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        Owner other = registerAndLogin("other@example.com", "Other Owner");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID ruleId = createdRuleId(createRule(owner, slackRule("Announce", "MANUAL", projectId, endUser, "en"))
                .andExpect(status().isCreated()));

        var memberSession = member(owner.organizationId(), "member@example.com");
        mockMvc.perform(get("/api/automation/rules").session(memberSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
        mockMvc.perform(get("/automation").session(memberSession)).andExpect(status().isForbidden());

        // Another Organization's rule is not forbidden, it simply does not exist.
        mockMvc.perform(get("/api/automation/rules/{ruleId}", ruleId).session(other.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("automation_rule_not_found"));
        mockMvc.perform(get("/api/automation/rules").session(other.session()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    private ResultActions execute(Owner owner, UUID ruleId, UUID releaseId, UUID requestId) throws Exception {
        return mockMvc.perform(post("/api/automation/rules/{ruleId}/execute", ruleId)
                .session(owner.session())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"releaseId\":\"%s\"%s}".formatted(
                        releaseId, requestId == null ? "" : ",\"requestId\":\"" + requestId + "\"")));
    }

    private UUID firstActionId(Owner owner, UUID ruleId) throws Exception {
        String body = mockMvc.perform(get("/api/automation/rules/{ruleId}", ruleId).session(owner.session()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.actions[0].id"));
    }
}
