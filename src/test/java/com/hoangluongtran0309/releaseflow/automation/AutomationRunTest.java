package com.hoangluongtran0309.releaseflow.automation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** How a run reads its actions, without a database in the way. */
class AutomationRunTest {

    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");

    @Test
    void waitsWhileAnActionIsStillToCome() {
        AutomationRun run = run();
        List<AutomationActionRun> actions = List.of(action(run, 0), action(run, 1));
        actions.getFirst().claim(NOW);
        run.actionClaimed(NOW);
        actions.getFirst().succeed("https://example.com/r/1", NOW);

        run.settle(actions, NOW);

        assertThat(run.getStatus()).isEqualTo(ExecutionStatus.PENDING);
        assertThat(run.getStartedAt()).isEqualTo(NOW);
        assertThat(run.getCompletedAt()).isNull();
    }

    @Test
    void endsAsSoonAsAnActionFailsOrCannotBeConfirmed() {
        AutomationRun failed = run();
        List<AutomationActionRun> failing = List.of(action(failed, 0), action(failed, 1));
        failing.getFirst().claim(NOW);
        failing.getFirst().fail("slack_rejected", NOW);
        failed.settle(failing, NOW);
        assertThat(failed.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(failed.getCompletedAt()).isEqualTo(NOW);

        // An unknown outcome outranks a failure: it needs a person either way.
        AutomationRun unknown = run();
        List<AutomationActionRun> mixed = List.of(action(unknown, 0), action(unknown, 1));
        mixed.get(0).claim(NOW);
        mixed.get(0).fail("slack_rejected", NOW);
        mixed.get(1).claim(NOW);
        mixed.get(1).markUnknown(ActionResult.OUTCOME_UNKNOWN, NOW);
        unknown.settle(mixed, NOW);
        assertThat(unknown.getStatus()).isEqualTo(ExecutionStatus.UNKNOWN);
    }

    @Test
    void succeedsOnlyOnceEveryActionHas() {
        AutomationRun run = run();
        List<AutomationActionRun> actions = List.of(action(run, 0), action(run, 1));
        actions.forEach(action -> {
            action.claim(NOW);
            action.succeed(null, NOW);
        });

        run.settle(actions, NOW);

        assertThat(run.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(actions).allSatisfy(action -> assertThat(action.getAttempts()).isOne());
    }

    @Test
    void cancellingStopsWhatHasNotStartedAndReopeningClearsIt() {
        AutomationRun run = run();
        List<AutomationActionRun> actions = List.of(action(run, 0), action(run, 1));
        run.requestCancellation();
        actions.forEach(action -> action.cancelPending(NOW));
        run.settle(actions, NOW);
        assertThat(run.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);
        assertThat(run.isCancellationRequested()).isTrue();

        run.reopen();
        assertThat(run.getStatus()).isEqualTo(ExecutionStatus.PENDING);
        assertThat(run.isCancellationRequested()).isFalse();
        assertThat(run.getCompletedAt()).isNull();
    }

    @Test
    void anActionBelongsToTheWorkerThatClaimedIt() {
        AutomationRun run = run();
        AutomationActionRun action = action(run, 0);
        action.claim(NOW);

        assertThat(action.isClaimedAt(NOW)).isTrue();
        assertThat(action.isClaimedAt(NOW.plusSeconds(1))).isFalse();

        action.markUnknown(ActionResult.OUTCOME_UNKNOWN, NOW);
        assertThat(action.isClaimedAt(NOW)).isFalse();
        action.retry();
        assertThat(action.getStatus()).isEqualTo(ExecutionStatus.PENDING);
        assertThat(action.getErrorCode()).isNull();
    }

    private static AutomationRun run() {
        AutomationRule rule = new AutomationRule(
                UUID.randomUUID(), UUID.randomUUID(), "Announce", TriggerType.MANUAL, null, NOW);
        return new AutomationRun(
                UUID.randomUUID(),
                rule,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "1.4.0",
                TriggerType.MANUAL,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Mai Tran",
                NOW
        );
    }

    private static AutomationActionRun action(AutomationRun run, int position) {
        return new AutomationActionRun(
                UUID.randomUUID(),
                run.getId(),
                run.getOrganizationId(),
                UUID.randomUUID(),
                position,
                ActionType.SLACK,
                UUID.randomUUID(),
                "End user",
                "en",
                "A note.",
                Map.of(),
                null
        );
    }
}
