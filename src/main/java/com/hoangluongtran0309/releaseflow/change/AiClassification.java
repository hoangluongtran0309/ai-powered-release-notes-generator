package com.hoangluongtran0309.releaseflow.change;

import java.util.Objects;

/**
 * One validated AI answer. The rules and review triggers decide what is kept.
 */
record AiClassification(
        ChangeCategory category,
        boolean breaking,
        boolean needsHumanReview,
        NeutralSummary summary
) {

    AiClassification {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
    }
}
