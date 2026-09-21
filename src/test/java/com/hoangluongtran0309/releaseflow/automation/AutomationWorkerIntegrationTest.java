package com.hoangluongtran0309.releaseflow.automation;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Duration;
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

class AutomationWorkerIntegrationTest extends AutomationIntegrationTestBase {

    @Autowired
    private AutomationWorker worker;

    @Autowired
    private io.micrometer.core.instrument.MeterRegistry registry;

    @Test
    void countsADeliveryOnlyOnceItsOutcomeIsWritten() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID ruleId = createdRuleId(createRule(owner,
                slackRule("Announce", "RELEASE_PUBLISHED", projectId, endUser, "en"))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        // Counters live as long as the process, and these tests share one, so every
        // assertion here is about what this test added.
        double succeededBefore = executions("release_published", "succeeded");
        double failedBefore = executions("release_published", "failed");
        double manualBefore = executions("manual", "succeeded");

        publishedRelease(owner, projectId, "1.4.0");
        // The outbox row alone is not a delivery.
        assertThat(executions("release_published", "succeeded")).isEqualTo(succeededBefore);

        deliverEverything();

        assertThat(executions("release_published", "succeeded")).isEqualTo(succeededBefore + 1);
        assertThat(executions("release_published", "failed")).isEqualTo(failedBefore);
        assertThat(executions("manual", "succeeded")).isEqualTo(manualBefore);
    }

    @Test
    void countsARefusedDeliveryAsFailedAndNotAsUnknown() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID ruleId = createdRuleId(createRule(owner,
                slackRule("Announce", "RELEASE_PUBLISHED", projectId, endUser, "en"))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        SLACK.respondWith(400);
        double failedBefore = executions("release_published", "failed");
        double unknownBefore = executions("release_published", "unknown");

        publishedRelease(owner, projectId, "1.4.0");
        deliverEverything();

        // A refusal is a decision somebody made, and never counted as unconfirmed.
        assertThat(executions("release_published", "failed")).isEqualTo(failedBefore + 1);
        assertThat(executions("release_published", "unknown")).isEqualTo(unknownBefore);
    }

    private double executions(String trigger, String outcome) {
        return registry.get(AutomationMetrics.EXECUTIONS)
                .tags("trigger", trigger, "outcome", outcome)
                .counter()
                .count();
    }

    @Test
    void publishingARuleCoversCreatesItsRunAfterTheReleaseIsCommitted() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID ruleId = createdRuleId(createRule(owner, slackRule("Announce", "RELEASE_PUBLISHED", projectId, endUser, "en"))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        UUID skipped = createdRuleId(createRule(owner, slackRule("Off", "RELEASE_PUBLISHED", projectId, endUser, "en"))
                .andExpect(status().isCreated()));

        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");

        // Publication only leaves an outbox row; nothing has run yet.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM automation_publish_jobs WHERE release_id = ?", String.class, releaseId))
                .isEqualTo("PENDING");
        assertThat(runCount()).isZero();

        assertThat(worker.processOne()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM automation_publish_jobs WHERE release_id = ?", String.class, releaseId))
                .isEqualTo("SUCCEEDED");
        assertThat(runCount()).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM automation_runs WHERE rule_id = ?", Integer.class, skipped)).isZero();

        assertThat(worker.processOne()).isTrue();
        assertThat(SLACK.messages()).hasSize(1);
        assertThat(SLACK.messages().getFirst()).startsWith("*Release 1.4.0*");
        mockMvc.perform(get("/api/automation/runs").session(owner.session()))
                .andExpect(jsonPath("$.items[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.items[0].triggerType").value("RELEASE_PUBLISHED"))
                .andExpect(jsonPath("$.items[0].actions[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.items[0].actions[0].attempts").value(1));

        // Nothing is left to do, and a second publication of the same release cannot happen.
        assertThat(worker.processOne()).isFalse();
    }

    @Test
    void publishesTheReleaseEvenWhenTheRuleHasNoNoteToDeliver() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        // The notes are written when the release is approved, so an audience added after
        // that has none. The rule then has nothing to deliver.
        UUID releaseId = approvedReleaseWithChange(owner, projectId, "1.4.0");
        UUID partners = createAudience(owner, "partners", "Partners");
        UUID ruleId = createdRuleId(createRule(owner, slackRule("Announce", "RELEASE_PUBLISHED", projectId, partners, "en"))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());

        mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/publish", projectId, releaseId)
                        .session(owner.session()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        assertThat(worker.processOne()).isTrue();
        assertThat(worker.processOne()).isTrue();

        assertThat(SLACK.messages()).isEmpty();
        mockMvc.perform(get("/api/automation/runs").session(owner.session()))
                .andExpect(jsonPath("$.items[0].status").value("FAILED"))
                .andExpect(jsonPath("$.items[0].actions[0].errorCode").value("release_note_missing"));
        mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}", projectId, releaseId)
                        .session(owner.session()))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void walksTheActionsInOrderAndStopsAtTheFirstFailure() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID operator = audienceId(owner, "operator");
        UUID ruleId = createdRuleId(createRule(owner, """
                {"name":"Announce","triggerType":"MANUAL","projectId":"%s","actions":[
                  {"actionType":"SLACK","audienceId":"%s","language":"en","secret":"%s"},
                  {"actionType":"SLACK","audienceId":"%s","language":"en","secret":"%s"}]}
                """.formatted(projectId, endUser, slackWebhook(), operator, slackWebhook()))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        UUID runId = run(owner, ruleId, publishedRelease(owner, projectId, "1.4.0"));

        SLACK.respondWith(400);
        assertThat(worker.processOne()).isTrue();

        mockMvc.perform(get("/api/automation/runs/{runId}", runId).session(owner.session()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.actions[0].status").value("FAILED"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("slack_rejected"))
                // The second action never ran, because the first one did not succeed.
                .andExpect(jsonPath("$.actions[1].status").value("PENDING"));
        assertThat(worker.processOne()).isFalse();

        // Running it again repeats only the action that failed, then carries on.
        SLACK.respondWith(200);
        mockMvc.perform(post("/api/automation/runs/{runId}/retry", runId).session(owner.session()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(worker.processOne()).isTrue();
        assertThat(worker.processOne()).isTrue();
        mockMvc.perform(get("/api/automation/runs/{runId}", runId).session(owner.session()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.actions[0].attempts").value(2))
                .andExpect(jsonPath("$.actions[1].attempts").value(1));
        assertThat(SLACK.messages()).hasSize(2);
    }

    @Test
    void anActionWhoseWorkerStoppedBecomesUnknownAndWaitsForAPerson() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID ruleId = createdRuleId(createRule(owner, slackRule("Announce", "MANUAL", projectId, endUser, "en"))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        UUID runId = run(owner, ruleId, publishedRelease(owner, projectId, "1.4.0"));

        // A claim nobody ever finished, older than the lease.
        jdbcTemplate.update("""
                UPDATE automation_action_runs SET status = 'RUNNING', claimed_at = now() - interval '6 minutes',
                    started_at = now() - interval '6 minutes', attempts = 1 WHERE run_id = ?
                """, runId);
        jdbcTemplate.update("UPDATE automation_runs SET status = 'RUNNING', started_at = now() WHERE id = ?", runId);

        assertThat(worker.processOne()).isFalse();
        mockMvc.perform(get("/api/automation/runs/{runId}", runId).session(owner.session()))
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.actions[0].status").value("UNKNOWN"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("execution_outcome_unknown"));
        assertThat(SLACK.messages()).isEmpty();

        // It is never sent again on its own, and repeating it takes a confirmation.
        mockMvc.perform(post("/api/automation/runs/{runId}/retry", runId).session(owner.session()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("automation_unknown_needs_confirmation"));
        mockMvc.perform(post("/api/automation/runs/{runId}/retry", runId)
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmUnknown\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(worker.processOne()).isTrue();
        assertThat(SLACK.messages()).hasSize(1);
    }

    @Test
    void cancellingLetsTheRunningActionFinishAndStopsTheRest() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID operator = audienceId(owner, "operator");
        UUID ruleId = createdRuleId(createRule(owner, """
                {"name":"Announce","triggerType":"MANUAL","projectId":"%s","actions":[
                  {"actionType":"SLACK","audienceId":"%s","language":"en","secret":"%s"},
                  {"actionType":"SLACK","audienceId":"%s","language":"en","secret":"%s"}]}
                """.formatted(projectId, endUser, slackWebhook(), operator, slackWebhook()))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        UUID runId = run(owner, ruleId, publishedRelease(owner, projectId, "1.4.0"));

        assertThat(worker.processOne()).isTrue();
        mockMvc.perform(post("/api/automation/runs/{runId}/cancel", runId).session(owner.session()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.actions[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.actions[1].status").value("CANCELLED"));

        assertThat(worker.processOne()).isFalse();
        assertThat(SLACK.messages()).hasSize(1);
    }

    @Test
    void concurrentWorkersDeliverEachActionOnce() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID ruleId = createdRuleId(createRule(owner, slackRule("Announce", "MANUAL", projectId, endUser, "en"))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        for (int release = 1; release <= 3; release++) {
            run(owner, ruleId, publishedRelease(owner, projectId, "1." + release + ".0"));
        }
        SLACK.delay(Duration.ofMillis(300));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> drains = List.of(drain(start), drain(start), drain(start), drain(start));
            start.countDown();
            workers.invokeAll(drains, 60, TimeUnit.SECONDS);
        } finally {
            workers.shutdownNow();
        }

        assertThat(SLACK.messages()).hasSize(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM automation_action_runs WHERE status <> 'SUCCEEDED'", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForList("SELECT attempts FROM automation_action_runs", Integer.class))
                .containsOnly(1);
    }

    private Callable<Void> drain(CountDownLatch start) {
        return () -> {
            start.await();
            while (worker.processOne()) {
                // Keep taking work until nothing is left.
            }
            return null;
        };
    }

    private Integer runCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM automation_runs", Integer.class);
    }

    /** Works the outbox dry, so a test's next tick is the delivery it means to watch. */
    private void drainOutbox() {
        while (jdbcTemplate.queryForObject(
                "SELECT count(*) FROM automation_publish_jobs WHERE status = 'PENDING'", Integer.class) > 0) {
            worker.processOne();
        }
    }

    private UUID run(Owner owner, UUID ruleId, UUID releaseId) throws Exception {
        drainOutbox();
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
