package com.hoangluongtran0309.releaseflow.change;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** What classification tells a deployment, and what it must never tell it. */
class ClassificationMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ClassificationMetrics metrics = new ClassificationMetrics(registry);

    @Test
    void registersEverySeriesAtZeroBeforeAnythingHappens() {
        // A panel shows a line at zero rather than "no data", and a rate never divides by
        // a series that is not there yet.
        assertThat(registry.find(ClassificationMetrics.COMPLETED).counters())
                .hasSize(2)
                .allSatisfy(counter -> assertThat(counter.count()).isZero());
        assertThat(registry.find(ClassificationMetrics.PROVIDER_REQUESTS).counters())
                .hasSize(AiProvider.values().length * 2)
                .allSatisfy(counter -> assertThat(counter.count()).isZero());
        assertThat(registry.find(ClassificationMetrics.COLLECT_TO_COMPLETE).timer()).isNotNull();
    }

    @Test
    void countsACompletionAndHowLongItsChangeWaited() {
        metrics.recordCompleted(true, Duration.ofSeconds(65));

        assertThat(registry.get(ClassificationMetrics.COMPLETED)
                .tag("needs_human_review", "true").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(ClassificationMetrics.COMPLETED)
                .tag("needs_human_review", "false").counter().count()).isZero();
        assertThat(registry.get(ClassificationMetrics.COLLECT_TO_COMPLETE).timer().count()).isEqualTo(1);
        assertThat(registry.get(ClassificationMetrics.COLLECT_TO_COMPLETE).timer()
                .totalTime(TimeUnit.SECONDS)).isEqualTo(65.0);
    }

    @Test
    void treatsAClockThatWentBackwardsAsNoTimeAtAll() {
        metrics.recordCompleted(false, Duration.ofSeconds(-30));

        assertThat(registry.get(ClassificationMetrics.COLLECT_TO_COMPLETE).timer().count()).isEqualTo(1);
        assertThat(registry.get(ClassificationMetrics.COLLECT_TO_COMPLETE).timer()
                .totalTime(TimeUnit.SECONDS)).isZero();
    }

    @Test
    void countsOneProviderRequestByItsOutcome() {
        metrics.recordProviderRequest(AiProvider.OPENAI, false);
        metrics.recordProviderRequest(AiProvider.ANTHROPIC, true);

        assertThat(registry.get(ClassificationMetrics.PROVIDER_REQUESTS)
                .tags("provider", "openai", "outcome", "error").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(ClassificationMetrics.PROVIDER_REQUESTS)
                .tags("provider", "anthropic", "outcome", "success").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(ClassificationMetrics.PROVIDER_REQUESTS)
                .tags("provider", "deepseek", "outcome", "success").counter().count()).isZero();
    }

    @Test
    void namesNothingThatCouldIdentifyATenantOrAChange() {
        metrics.recordCompleted(true, Duration.ofSeconds(1));
        metrics.recordProviderRequest(AiProvider.DEEPSEEK, true);

        List<String> tagKeys = registry.getMeters().stream()
                .map(Meter::getId)
                .flatMap(id -> id.getTags().stream())
                .map(Tag::getKey)
                .distinct()
                .toList();

        // "le" is the bucket boundary Micrometer adds for the timer's own histogram.
        assertThat(tagKeys).containsOnly("needs_human_review", "provider", "outcome", "le");
    }
}
