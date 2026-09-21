package com.hoangluongtran0309.releaseflow.automation;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How automation's deliveries ended, counted by what set them off. Both tags come from
 * enums, so the series are fixed at startup; no rule, run, action, release, or provider
 * reference is ever a tag.
 *
 * <p>{@code FAILED} and {@code UNKNOWN} are deliberately separate. A failure is a decision
 * somebody made; an unknown outcome may have been delivered, and reporting it as a failure
 * would tell an operator something untrue.
 */
@Component
public class AutomationMetrics {

    static final String EXECUTIONS = "releaseflow.automation.action.executions";

    /** The ways a delivery can end. The other statuses describe a run, not an outcome. */
    private static final List<ExecutionStatus> OUTCOMES =
            List.of(ExecutionStatus.SUCCEEDED, ExecutionStatus.FAILED, ExecutionStatus.UNKNOWN);

    private final Map<TriggerType, Map<ExecutionStatus, Counter>> executions = new EnumMap<>(TriggerType.class);

    AutomationMetrics(MeterRegistry registry) {
        for (TriggerType trigger : TriggerType.values()) {
            Map<ExecutionStatus, Counter> outcomes = new EnumMap<>(ExecutionStatus.class);
            for (ExecutionStatus outcome : OUTCOMES) {
                outcomes.put(outcome, Counter.builder(EXECUTIONS)
                        .description("Automation action deliveries, by trigger and outcome")
                        .tag("trigger", tag(trigger))
                        .tag("outcome", tag(outcome))
                        .register(registry));
            }
            executions.put(trigger, outcomes);
        }
    }

    public void recordExecution(TriggerType trigger, ExecutionStatus outcome) {
        recordExecutions(trigger, outcome, 1);
    }

    /** Several at once, as recovering abandoned actions produces. A count of none is not an event. */
    public void recordExecutions(TriggerType trigger, ExecutionStatus outcome, long count) {
        if (count <= 0) {
            return;
        }
        Counter counter = executions.get(trigger).get(outcome);
        if (counter == null) {
            throw new IllegalArgumentException(outcome + " is not how a delivery ends.");
        }
        counter.increment(count);
    }

    private static String tag(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
