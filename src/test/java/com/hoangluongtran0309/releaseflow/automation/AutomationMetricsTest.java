package com.hoangluongtran0309.releaseflow.automation;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What automation tells a deployment about its deliveries. */
class AutomationMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AutomationMetrics metrics = new AutomationMetrics(registry);

    @Test
    void registersEveryTriggerAndOutcomeAtZero() {
        assertThat(registry.find(AutomationMetrics.EXECUTIONS).counters())
                .hasSize(TriggerType.values().length * 3)
                .allSatisfy(counter -> assertThat(counter.count()).isZero());
    }

    @Test
    void countsADeliveryByWhatSetItOffAndHowItEnded() {
        metrics.recordExecution(TriggerType.RELEASE_PUBLISHED, ExecutionStatus.SUCCEEDED);
        metrics.recordExecution(TriggerType.EXTERNAL_WEBHOOK, ExecutionStatus.FAILED);
        // Recovering abandoned actions reports several at once.
        metrics.recordExecutions(TriggerType.SCHEDULED_CRON, ExecutionStatus.UNKNOWN, 2);

        assertThat(count("release_published", "succeeded")).isEqualTo(1.0);
        assertThat(count("external_webhook", "failed")).isEqualTo(1.0);
        assertThat(count("scheduled_cron", "unknown")).isEqualTo(2.0);
        assertThat(count("manual", "succeeded")).isZero();
    }

    @Test
    void countsNothingWhenThereWasNothingToRecover() {
        metrics.recordExecutions(TriggerType.MANUAL, ExecutionStatus.UNKNOWN, 0);

        assertThat(count("manual", "unknown")).isZero();
    }

    @Test
    void refusesAStatusThatIsNotHowADeliveryEnds() {
        assertThatThrownBy(() -> metrics.recordExecution(TriggerType.MANUAL, ExecutionStatus.RUNNING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void namesNothingButTheTriggerAndTheOutcome() {
        metrics.recordExecution(TriggerType.MANUAL, ExecutionStatus.SUCCEEDED);

        List<String> tagKeys = registry.getMeters().stream()
                .map(Meter::getId)
                .flatMap(id -> id.getTags().stream())
                .map(Tag::getKey)
                .distinct()
                .toList();

        assertThat(tagKeys).containsOnly("trigger", "outcome");
    }

    private double count(String trigger, String outcome) {
        return registry.get(AutomationMetrics.EXECUTIONS)
                .tags("trigger", trigger, "outcome", outcome)
                .counter()
                .count();
    }
}
