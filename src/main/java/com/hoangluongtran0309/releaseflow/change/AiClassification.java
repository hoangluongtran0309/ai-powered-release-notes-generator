package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.category.CategoryRef;
import com.hoangluongtran0309.releaseflow.category.CategorySuggestionDraft;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One validated AI answer. The rules and review triggers decide what is kept.
 *
 * @param category a category of the catalog the AI was given
 * @param narratives the change explained for each audience, keyed by audience code;
 *     an audience the AI wrote nothing for is absent
 * @param suggestion a new category the AI proposed because none fit, or null; only
 *     present when {@code category} is Unknown
 * @param contextScore how much evidence the pull request gave, from 0 to 100
 * @param contextReasons short codes for missing evidence
 */
record AiClassification(
        CategoryRef category,
        boolean breaking,
        boolean needsHumanReview,
        NeutralSummary summary,
        Map<String, String> narratives,
        CategorySuggestionDraft suggestion,
        int contextScore,
        List<String> contextReasons
) {

    AiClassification {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        narratives = Map.copyOf(narratives);
        contextReasons = List.copyOf(contextReasons);
        if (contextScore < 0 || contextScore > 100) {
            throw new IllegalArgumentException("A context score is between 0 and 100.");
        }
        if (suggestion != null && !category.isUnknown()) {
            throw new IllegalArgumentException("Only an Unknown answer carries a category suggestion.");
        }
    }
}
