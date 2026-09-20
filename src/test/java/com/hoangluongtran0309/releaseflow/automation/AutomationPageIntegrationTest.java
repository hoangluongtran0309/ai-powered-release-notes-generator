package com.hoangluongtran0309.releaseflow.automation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The Automation page does everything the REST endpoints do. */
class AutomationPageIntegrationTest extends AutomationIntegrationTestBase {

    @Autowired
    private AutomationWorker worker;

    @Test
    void thePageWritesEnablesAndRunsARule() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");

        mockMvc.perform(get("/automation").session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No rules yet.")))
                .andExpect(content().string(containsString("Nothing has run yet.")));

        mockMvc.perform(post("/automation").session(owner.session()).with(csrf())
                        .param("name", "Announce")
                        .param("triggerType", "MANUAL")
                        .param("projectId", projectId.toString())
                        .param("actions[0].actionType", "SLACK")
                        .param("actions[0].audienceId", endUser.toString())
                        .param("actions[0].language", "en")
                        .param("actions[0].secret", slackWebhook()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/automation?saved"));

        UUID ruleId = UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM automation_rules WHERE name = 'Announce'", String.class));
        mockMvc.perform(get("/automation").session(owner.session()))
                .andExpect(content().string(containsString("rule-" + ruleId)))
                .andExpect(content().string(containsString("Disabled")))
                // A stored secret never reaches a page.
                .andExpect(content().string(not(containsString(slackWebhook()))));

        mockMvc.perform(post("/automation/{ruleId}/enable", ruleId).session(owner.session()).with(csrf()))
                .andExpect(redirectedUrl("/automation?enabled"));
        mockMvc.perform(post("/automation/{ruleId}/execute", ruleId).session(owner.session()).with(csrf())
                        .param("releaseId", releaseId.toString())
                        .param("requestId", UUID.randomUUID().toString()))
                .andExpect(redirectedUrl("/automation?executed"));

        while (worker.processOne()) {
            // Deliver everything the page queued.
        }
        assertThat(SLACK.messages()).hasSize(1);
        mockMvc.perform(get("/automation").session(owner.session()))
                .andExpect(content().string(containsString("Succeeded")));
    }

    @Test
    void thePageShowsWhyARuleWasRefusedAndKeepsTheForm() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");

        mockMvc.perform(post("/automation").session(owner.session()).with(csrf())
                        .param("name", "")
                        .param("triggerType", "MANUAL"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Name is required.")));

        mockMvc.perform(post("/automation").session(owner.session()).with(csrf())
                        .param("name", "Elsewhere")
                        .param("triggerType", "MANUAL")
                        .param("projectId", projectId.toString())
                        .param("actions[0].actionType", "SLACK")
                        .param("actions[0].audienceId", endUser.toString())
                        .param("actions[0].language", "en")
                        .param("actions[0].secret", "https://example.com/services/x"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("hooks.slack.com")));
    }

    @Test
    void thePageArchivesARuleAndCancelsTheRunsItHadNotFinished() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        UUID ruleId = createdRuleId(createRule(owner, slackRule("Announce", "MANUAL", projectId, endUser, "en"))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        mockMvc.perform(post("/automation/{ruleId}/execute", ruleId).session(owner.session()).with(csrf())
                        .param("releaseId", releaseId.toString())
                        .param("requestId", UUID.randomUUID().toString()))
                .andExpect(redirectedUrl("/automation?executed"));

        mockMvc.perform(post("/automation/{ruleId}/archive", ruleId).session(owner.session()).with(csrf()))
                .andExpect(redirectedUrl("/automation?archived"));

        mockMvc.perform(get("/automation").session(owner.session()))
                .andExpect(content().string(containsString("No rules yet.")))
                .andExpect(content().string(containsString("Cancelled")));
        assertThat(worker.processOne()).isTrue();
        assertThat(SLACK.messages()).isEmpty();
    }

    @Test
    void thePageEditsARuleWithoutAskingForItsSecretAgain() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID ruleId = createdRuleId(createRule(owner, slackRule("Announce", "MANUAL", projectId, endUser, "en"))
                .andExpect(status().isCreated()));
        UUID actionId = UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM automation_rule_actions WHERE rule_id = ?", String.class, ruleId));

        mockMvc.perform(get("/automation/{ruleId}", ruleId).session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Edit rule")))
                .andExpect(content().string(containsString("value=\"Announce\"")));

        mockMvc.perform(post("/automation/{ruleId}", ruleId).session(owner.session()).with(csrf())
                        .param("name", "Announce loudly")
                        .param("triggerType", "MANUAL")
                        .param("projectId", projectId.toString())
                        .param("actions[0].id", actionId.toString())
                        .param("actions[0].actionType", "SLACK")
                        .param("actions[0].audienceId", endUser.toString())
                        .param("actions[0].language", "en")
                        .param("actions[0].secret", ""))
                .andExpect(redirectedUrl("/automation?saved"));

        // The action kept its identity, so the secret it already had still decrypts.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT id::text FROM automation_rule_actions WHERE rule_id = ?", String.class, ruleId))
                .isEqualTo(actionId.toString());
        enable(owner, ruleId).andExpect(status().isOk());
        mockMvc.perform(get("/automation").session(owner.session()))
                .andExpect(content().string(containsString("Announce loudly")))
                .andExpect(content().string(containsString("Enabled")));
    }

    @Test
    void thePageWritesAScheduleAndSaysWhenItWouldRun() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");

        mockMvc.perform(post("/automation/cron-preview").session(owner.session()).with(csrf())
                        .param("cronExpression", "0 0 9 * * MON")
                        .param("cronTimeZone", "Europe/Berlin"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("That schedule next runs")));

        mockMvc.perform(post("/automation").session(owner.session()).with(csrf())
                        .param("name", "Weekly digest")
                        .param("triggerType", "SCHEDULED_CRON")
                        .param("projectId", projectId.toString())
                        .param("releaseId", releaseId.toString())
                        .param("cronExpression", "0 0 9 * * MON")
                        .param("cronTimeZone", "Europe/Berlin")
                        .param("actions[0].actionType", "SLACK")
                        .param("actions[0].audienceId", endUser.toString())
                        .param("actions[0].language", "en")
                        .param("actions[0].secret", slackWebhook()))
                .andExpect(redirectedUrl("/automation?saved"));

        UUID ruleId = UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM automation_rules WHERE name = 'Weekly digest'", String.class));
        mockMvc.perform(post("/automation/{ruleId}/enable", ruleId).session(owner.session()).with(csrf()))
                .andExpect(redirectedUrl("/automation?enabled"));
        mockMvc.perform(get("/automation").session(owner.session()))
                .andExpect(content().string(containsString("0 0 9 * * MON · Europe/Berlin")))
                .andExpect(content().string(containsString("Next")));

        // A reminder is written the same way, and says how much notice it gives.
        mockMvc.perform(post("/automation").session(owner.session()).with(csrf())
                        .param("name", "Warn the team")
                        .param("triggerType", "UPCOMING_RELEASE_REMINDER")
                        .param("daysBefore", "3")
                        .param("actions[0].actionType", "SLACK")
                        .param("actions[0].audienceId", endUser.toString())
                        .param("actions[0].language", "en")
                        .param("actions[0].secret", slackWebhook()))
                .andExpect(redirectedUrl("/automation?saved"));
        mockMvc.perform(get("/automation").session(owner.session()))
                .andExpect(content().string(containsString("3 day(s) before")));
    }

    @Test
    void thePageShowsAWebhookSecretOnceAndRotatesIt() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");

        String revealed = mockMvc.perform(post("/automation").session(owner.session()).with(csrf())
                        .param("name", "Called from outside")
                        .param("triggerType", "EXTERNAL_WEBHOOK")
                        .param("projectId", projectId.toString())
                        .param("actions[0].actionType", "SLACK")
                        .param("actions[0].audienceId", endUser.toString())
                        .param("actions[0].language", "en")
                        .param("actions[0].secret", slackWebhook()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Save this webhook secret now")))
                .andReturn().getResponse().getContentAsString();

        UUID ruleId = UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM automation_rules WHERE name = 'Called from outside'", String.class));
        String path = jdbcTemplate.queryForObject(
                "SELECT '/webhooks/automation/' || webhook_id FROM automation_rules WHERE id = ?",
                String.class, ruleId);
        assertThat(revealed).contains(path);

        // The list shows the path, never the secret.
        mockMvc.perform(get("/automation").session(owner.session()))
                .andExpect(content().string(containsString(path)))
                .andExpect(content().string(containsString("Rotate secret")));

        mockMvc.perform(post("/automation/{ruleId}/webhook-secret/rotate", ruleId)
                        .session(owner.session()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Save this webhook secret now")))
                .andExpect(content().string(containsString(path)));
    }

    @Test
    void membersNeverSeeAutomation() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        var memberSession = member(owner.organizationId(), "member@example.com");

        mockMvc.perform(get("/").session(memberSession))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/automation\""))));
        mockMvc.perform(get("/automation").session(memberSession)).andExpect(status().isForbidden());
        mockMvc.perform(get("/").session(owner.session()))
                .andExpect(content().string(containsString("href=\"/automation\"")));
    }
}
