package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The enricher of each source type. Every type has exactly one. */
@Component
class SourceEnrichers {

    private final Map<SourceType, SourceEnricher> byType;

    SourceEnrichers(List<SourceEnricher> enrichers) {
        this.byType = enrichers.stream()
                .collect(Collectors.toUnmodifiableMap(SourceEnricher::sourceType, Function.identity()));
        for (SourceType sourceType : SourceType.values()) {
            if (!byType.containsKey(sourceType)) {
                throw new IllegalStateException("No enricher for source type " + sourceType + ".");
            }
        }
    }

    SourceEnricher of(SourceType sourceType) {
        return byType.get(sourceType);
    }
}
