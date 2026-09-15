package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.category.CategoryRef;
import com.hoangluongtran0309.releaseflow.category.CategorySuggestionDraft;

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
 */
record AiClassification(
        CategoryRef category,
        boolean breaking,
        boolean needsHumanReview,
        NeutralSummary summary,
        Map<String, String> narratives,
        CategorySuggestionDraft suggestion
) {

    AiClassification {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        narratives = Map.copyOf(narratives);
        if (suggestion != null && !category.isUnknown()) {
            throw new IllegalArgumentException("Only an Unknown answer carries a category suggestion.");
        }
    }
}
