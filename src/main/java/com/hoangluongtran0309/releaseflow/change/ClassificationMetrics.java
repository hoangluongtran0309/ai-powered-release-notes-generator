package com.hoangluongtran0309.releaseflow.change;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * What a deployment may know about classification, and nothing more. Every tag comes from
 * a finite set — a boolean, a provider, an outcome — so the number of series is fixed at
 * startup and cannot grow with the number of Organizations, Projects, or changes. Nothing
 * here carries a tenant, an identifier, a model name, or the text of an error.
 *
 * <p>Every series is registered at zero when the application starts, so a panel shows a
 * line at zero rather than "no data" before the first change arrives, and a query never
 * has to divide by a series that is not there yet.
 */
@Component
public class ClassificationMetrics {

    static final String COMPLETED = "releaseflow.classification.completed";
    static final String COLLECT_TO_COMPLETE = "releaseflow.classification.collect_to_complete";
    static final String PROVIDER_REQUESTS = "releaseflow.classification.provider.requests";

    private final Map<Boolean, Counter> completed = new HashMap<>();
    private final Map<AiProvider, Map<Boolean, Counter>> providerRequests = new EnumMap<>(AiProvider.class);
    private final Timer collectToComplete;

    ClassificationMetrics(MeterRegistry registry) {
        for (boolean needsHumanReview : new boolean[] {false, true}) {
            completed.put(needsHumanReview, Counter.builder(COMPLETED)
                    .description("Changes whose classification was committed, by review requirement")
                    .tag("needs_human_review", Boolean.toString(needsHumanReview))
                    .register(registry));
        }
        for (AiProvider provider : AiProvider.values()) {
            Map<Boolean, Counter> outcomes = new HashMap<>();
            for (boolean succeeded : new boolean[] {false, true}) {
                outcomes.put(succeeded, Counter.builder(PROVIDER_REQUESTS)
                        .description("Automatic classification requests, by provider and outcome")
                        .tag("provider", provider.getValue())
                        .tag("outcome", succeeded ? "success" : "error")
                        .register(registry));
            }
            providerRequests.put(provider, outcomes);
        }
        // No percentiles are published from here: the buckets below are what Grafana reads,
        // and a quantile computed per process could not be aggregated across replicas.
        collectToComplete = Timer.builder(COLLECT_TO_COMPLETE)
                .description("Time from recording a change to committing its classification")
                .serviceLevelObjectives(
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(15),
                        Duration.ofSeconds(30),
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(2),
                        Duration.ofMinutes(5),
                        Duration.ofMinutes(10))
                .register(registry);
    }

    /** One committed classification, and how long the change waited for it. */
    public void recordCompleted(boolean needsHumanReview, Duration collectToCompleteDuration) {
        completed.get(needsHumanReview).increment();
        // A clock that went backwards must not corrupt the timer's total.
        collectToComplete.record(collectToCompleteDuration.isNegative() ? Duration.ZERO : collectToCompleteDuration);
    }

    /** Exactly one of these per request a provider was asked to answer. */
    public void recordProviderRequest(AiProvider provider, boolean succeeded) {
        providerRequests.get(provider).get(succeeded).increment();
    }
}
