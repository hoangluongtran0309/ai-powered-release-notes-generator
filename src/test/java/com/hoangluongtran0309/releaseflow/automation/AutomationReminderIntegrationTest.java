package com.hoangluongtran0309.releaseflow.automation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Telling people about an approved release before it goes out. */
class AutomationReminderIntegrationTest extends AutomationIntegrationTestBase {

    @Autowired
    private AutomationTriggerWorker triggerWorker;

    @Autowired
    private AutomationWorker worker;

    @Test
    void remindsOnceWhenThePlannedTimeIsWithinTheNotice() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID releaseId = approvedReleaseWithChange(owner, projectId, "1.4.0");
        UUID ruleId = createdRuleId(createRule(owner, reminderRule("Warn the team", projectId, endUser, 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reminderDaysBefore").value(3)));
        enable(owner, ruleId).andExpect(status().isOk());

        // Still eight days out: three days' notice has not been reached.
        schedule(owner, projectId, releaseId, Instant.now().plus(8, ChronoUnit.DAYS));
        assertThat(triggerWorker.processOne()).isFalse();

        Instant planned = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        schedule(owner, projectId, releaseId, planned);

        assertThat(triggerWorker.processOne()).isTrue();
        assertThat(runCount()).isOne();
        assertThat(scheduledFor(ruleId)).isEqualTo(planned.minus(Duration.ofDays(3)));
        // Looking again changes nothing: the same moment is the same reminder.
        assertThat(triggerWorker.processOne()).isFalse();

        assertThat(worker.processOne()).isTrue();
        assertThat(SLACK.messages()).hasSize(1);
        mockMvc.perform(get("/api/automation/runs").session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].triggerType").value("UPCOMING_RELEASE_REMINDER"))
                .andExpect(jsonPath("$.items[0].status").value("SUCCEEDED"));
    }

    @Test
    void remindsOnceMoreWhenTheReleaseIsMoved() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID releaseId = approvedReleaseWithChange(owner, projectId, "1.4.0");
        UUID ruleId = createdRuleId(createRule(owner, reminderRule("Warn the team", projectId, endUser, 3))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());

        schedule(owner, projectId, releaseId, Instant.now().plus(2, ChronoUnit.DAYS));
        assertThat(triggerWorker.processOne()).isTrue();

        // A new plan is a new moment to answer, and so one further reminder.
        schedule(owner, projectId, releaseId, Instant.now().plus(1, ChronoUnit.DAYS));
        assertThat(triggerWorker.processOne()).isTrue();
        assertThat(triggerWorker.processOne()).isFalse();
        assertThat(runCount()).isEqualTo(2);
    }

    @Test
    void remindsAsTheReleaseComesDueWhenThereIsNoNotice() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID releaseId = approvedReleaseWithChange(owner, projectId, "1.4.0");
        UUID ruleId = createdRuleId(createRule(owner, reminderRule("As it goes out", projectId, endUser, 0))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reminderDaysBefore").value(0)));
        enable(owner, ruleId).andExpect(status().isOk());

        schedule(owner, projectId, releaseId, Instant.now().plus(1, ChronoUnit.HOURS));
        assertThat(triggerWorker.processOne()).isFalse();

        // Inside the lookahead the deployment gives a reminder with no notice at all.
        schedule(owner, projectId, releaseId, Instant.now().plusSeconds(5));
        assertThat(triggerWorker.processOne()).isTrue();
        assertThat(runCount()).isOne();
    }

    @Test
    void neverRemindsAboutAnotherOrganizationsRelease() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID releaseId = approvedReleaseWithChange(owner, projectId, "1.4.0");
        schedule(owner, projectId, releaseId, Instant.now().plus(1, ChronoUnit.DAYS));

        Owner other = registerAndLogin("other@example.com", "Linh Pham");
        UUID otherProject = createProject(other);
        UUID otherRule = createdRuleId(createRule(
                other, reminderRule("Warn the team", otherProject, audienceId(other, "end_user"), 3))
                .andExpect(status().isCreated()));
        enable(other, otherRule).andExpect(status().isOk());

        assertThat(triggerWorker.processOne()).isFalse();
        assertThat(runCount()).isZero();
    }

    @Test
    void remindsOnlyForItsOwnProjectWhenItNamesOne() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID watched = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID ruleId = createdRuleId(createRule(owner, reminderRule("Warn the team", watched, endUser, 3))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());

        UUID otherProject = createProject(owner, "Checkout");
        UUID elsewhere = approvedReleaseWithChange(owner, otherProject, "9.9.9");
        schedule(owner, otherProject, elsewhere, Instant.now().plus(1, ChronoUnit.DAYS));

        assertThat(triggerWorker.processOne()).isFalse();
        assertThat(runCount()).isZero();
    }

    private Instant scheduledFor(UUID ruleId) {
        java.sql.Timestamp occurrence = jdbcTemplate.queryForObject(
                "SELECT scheduled_for FROM automation_runs WHERE rule_id = ?", java.sql.Timestamp.class, ruleId);
        return occurrence == null ? null : occurrence.toInstant();
    }

    private Integer runCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM automation_runs", Integer.class);
    }
}
