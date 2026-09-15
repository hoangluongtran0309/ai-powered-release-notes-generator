package com.hoangluongtran0309.releaseflow.change;

import java.util.Map;
import java.util.Objects;

/**
 * One validated AI answer. The rules and review triggers decide what is kept.
 *
 * @param narratives the change explained for each audience, keyed by audience code;
 *     an audience the AI wrote nothing for is absent
 */
record AiClassification(
        ChangeCategory category,
        boolean breaking,
        boolean needsHumanReview,
        NeutralSummary summary,
        Map<String, String> narratives
) {

    AiClassification {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        narratives = Map.copyOf(narratives);
    }
}
