package com.hoangluongtran0309.releaseflow.change;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Thresholds for the review signals. They are checked at startup, so a typo cannot
 * switch a signal off or make it fire on everything.
 */
@Component
class ClassificationSettings {

    private final int contextThreshold;
    private final double duplicateThreshold;

    ClassificationSettings(
            @Value("${releaseflow.classification.context-threshold}") int contextThreshold,
            @Value("${releaseflow.classification.duplicate-threshold}") double duplicateThreshold
    ) {
        if (contextThreshold < 0 || contextThreshold > 100) {
            throw new IllegalStateException("releaseflow.classification.context-threshold must be between 0 and 100.");
        }
        if (duplicateThreshold <= 0 || duplicateThreshold > 1) {
            throw new IllegalStateException(
                    "releaseflow.classification.duplicate-threshold must be greater than 0 and at most 1.");
        }
        this.contextThreshold = contextThreshold;
        this.duplicateThreshold = duplicateThreshold;
    }

    /** A context score below this needs review. */
    int contextThreshold() {
        return contextThreshold;
    }

    /** A pair of changes at least this similar is a duplicate candidate. */
    double duplicateThreshold() {
        return duplicateThreshold;
    }
}
