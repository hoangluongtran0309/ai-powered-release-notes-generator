package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The changed-file collector of each source type. Every type has exactly one. */
@Component
class ChangedFileCollectors {

    private final Map<SourceType, ChangedFileCollector> byType;

    ChangedFileCollectors(List<ChangedFileCollector> collectors) {
        this.byType = collectors.stream()
                .collect(Collectors.toUnmodifiableMap(ChangedFileCollector::sourceType, Function.identity()));
        for (SourceType sourceType : SourceType.values()) {
            if (!byType.containsKey(sourceType)) {
                throw new IllegalStateException("No changed-file collector for source type " + sourceType + ".");
            }
        }
    }

    ChangedFileCollector of(SourceType sourceType) {
        return byType.get(sourceType);
    }
}
