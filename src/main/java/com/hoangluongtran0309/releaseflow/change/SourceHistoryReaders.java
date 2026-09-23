package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The history reader of each source type that has a history to read. */
@Component
class SourceHistoryReaders {

    private final Map<SourceType, SourceHistoryReader> byType;

    SourceHistoryReaders(List<SourceHistoryReader> readers) {
        this.byType = readers.stream()
                .collect(Collectors.toUnmodifiableMap(SourceHistoryReader::sourceType, Function.identity()));
        for (SourceType sourceType : SourceType.values()) {
            // A source type without a history to read needs no reader.
            if (sourceType.supportsHistoryImport() && !byType.containsKey(sourceType)) {
                throw new IllegalStateException("No history reader for source type " + sourceType + ".");
            }
        }
    }

    SourceHistoryReader of(SourceType sourceType) {
        return byType.get(sourceType);
    }
}
