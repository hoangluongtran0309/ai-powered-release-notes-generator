package com.hoangluongtran0309.releaseflow.automation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A rule ReleaseFlow sets off itself, on a schedule of the Organization's choosing. */
class AutomationTriggerIntegrationTest extends AutomationIntegrationTestBase {

    private static final String EVERY_MORNING = "0 0 9 * * *";
    private static final String BERLIN = "Europe/Berlin";

    @Autowired
    private AutomationTriggerWorker triggerWorker;

    @Test
    void firesAScheduleThatHasComeRoundAndBooksTheNextOne() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        UUID ruleId = createdRuleId(createRule(
                owner, cronRule("Weekly digest", projectId, endUser, releaseId, EVERY_MORNING, BERLIN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cronExpression").value(EVERY_MORNING))
                .andExpect(jsonPath("$.cronTimeZone").value(BERLIN))
                // A rule that has not been enabled is not booked for anything yet.
                .andExpect(jsonPath("$.nextFireAt").doesNotExist()));

        enable(owner, ruleId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextFireAt").exists());
        assertThat(nextFireAt(ruleId)).isAfter(Instant.now());

        // Nothing is due until its moment arrives.
        assertThat(triggerWorker.processOne()).isFalse();

        Instant due = Instant.now().minusSeconds(30).truncatedTo(ChronoUnit.MICROS);
        setNextFireAt(ruleId, due);

        assertThat(triggerWorker.processOne()).isTrue();
        assertThat(runCount()).isOne();
        assertThat(nextFireAt(ruleId)).isAfter(Instant.now());
        assertThat(scheduledFor(ruleId)).isEqualTo(due);
        assertThat(triggerWorker.processOne()).isFalse();

        // The delivery itself is the usual one, and the history says what set it off.
        deliverEverything();
        assertThat(SLACK.messages()).hasSize(1);
        mockMvc.perform(get("/api/automation/runs").session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].triggerType").value("SCHEDULED_CRON"))
                .andExpect(jsonPath("$.items[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.items[0].initiatorName").doesNotExist());
    }

    @Test
    void collapsesEveryFiringItMissedIntoOneCatchUpRun() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        UUID ruleId = createdRuleId(createRule(
                owner, cronRule("Weekly digest", projectId, endUser, releaseId, EVERY_MORNING, BERLIN))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());

        // A week of downtime: seven mornings went by unanswered.
        setNextFireAt(ruleId, Instant.now().minus(7, ChronoUnit.DAYS));

        assertThat(triggerWorker.processOne()).isTrue();
        assertThat(triggerWorker.processOne()).isFalse();
        assertThat(runCount()).isOne();
        assertThat(nextFireAt(ruleId)).isAfter(Instant.now());
    }

    @Test
    void neverFiresASchedulePutAwayOrTurnedOff() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        UUID ruleId = createdRuleId(createRule(
                owner, cronRule("Weekly digest", projectId, endUser, releaseId, EVERY_MORNING, BERLIN))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        setNextFireAt(ruleId, Instant.now().minusSeconds(30));

        mockMvc.perform(post("/api/automation/rules/{ruleId}/disable", ruleId).session(owner.session()).with(csrf()))
                .andExpect(status().isOk());

        // Turning a rule off takes its booking away, so it cannot come round again.
        assertThat(nextFireAt(ruleId)).isNull();
        assertThat(triggerWorker.processOne()).isFalse();
        assertThat(runCount()).isZero();
    }

    @Test
    void twoWorkersAnswerOneOccurrenceOnce() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        UUID ruleId = createdRuleId(createRule(
                owner, cronRule("Weekly digest", projectId, endUser, releaseId, EVERY_MORNING, BERLIN))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        setNextFireAt(ruleId, Instant.now().minusSeconds(30));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> ticks = List.of(tick(start), tick(start), tick(start), tick(start));
            start.countDown();
            workers.invokeAll(ticks, 60, TimeUnit.SECONDS);
        } finally {
            workers.shutdownNow();
        }

        assertThat(runCount()).isOne();
    }

    @Test
    void keepsScheduledRulesInsideTheirOwnOrganization() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID releaseId = publishedRelease(owner, createProject(owner), "1.4.0");

        Owner other = registerAndLogin("other@example.com", "Linh Pham");

        // Another Organization's release is not a release this Organization can repeat.
        createRule(other, cronRule("Weekly digest", createProject(other), audienceId(other, "end_user"),
                releaseId, EVERY_MORNING, BERLIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("automation_cron_release_required"));
        assertThat(runCount()).isZero();
    }

    private Callable<Void> tick(CountDownLatch start) {
        return () -> {
            start.await();
            while (triggerWorker.processOne()) {
                // Keep taking work until nothing is due.
            }
            return null;
        };
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
