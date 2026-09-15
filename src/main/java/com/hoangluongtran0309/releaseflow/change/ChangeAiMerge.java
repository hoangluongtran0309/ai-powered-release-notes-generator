package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.category.CategoryRef;
import com.hoangluongtran0309.releaseflow.category.CategorySuggestionDraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Combines the fixed rules with one AI outcome (ADR-0009). The rules always win: a
 * category they chose is kept, the AI can add a breaking flag but never remove one,
 * and review triggers still force review. The AI settles a change only when the
 * rules left it Unknown and nothing else requires a person. A category the AI proposed
 * instead of choosing one keeps the change Unknown and adds a trigger until an
 * administrator decides the proposal.
 */
final class ChangeAiMerge {

    private ChangeAiMerge() {
    }

    /**
     * @param ai the AI outcome, or null when no AI provider is configured
     */
    static ClassifiedChange merge(ChangeClassification rules, AiOutcome ai) {
        if (ai == null) {
            return new ClassifiedChange(rules, ClassificationSource.RULES, null, null);
        }
        if (!ai.succeeded()) {
            List<ReviewTrigger> triggers = new ArrayList<>(rules.triggers());
            triggers.add(ReviewTrigger.classifierFallback());
            return new ClassifiedChange(
                    new ChangeClassification(rules.category(), rules.breaking(), true, rules.reasons(), triggers),
                    ClassificationSource.RULES,
                    ai,
                    null
            );
        }

        AiClassification answer = ai.classification();
        List<String> reasons = new ArrayList<>(rules.reasons());
        boolean aiChoseCategory = rules.category().isUnknown() && !answer.category().isUnknown();
        CategoryRef category = aiChoseCategory ? answer.category() : rules.category();
        List<ReviewTrigger> triggers = new ArrayList<>(rules.triggers());
        CategorySuggestionDraft suggestion = rules.category().isUnknown() ? answer.suggestion() : null;
        if (suggestion != null) {
            triggers.add(ReviewTrigger.categorySuggestion(suggestion.code()));
            reasons.add("AI proposed a new category \"" + suggestion.code() + "\"");
        }
        if (aiChoseCategory) {
            reasons.add("Category from AI (" + ai.provider().getLabel() + " " + ai.model() + ")");
        }
        boolean breaking = rules.breaking() || answer.breaking();
        if (answer.breaking() && !rules.breaking()) {
            reasons.add("AI marked it breaking");
        }
        if (answer.needsHumanReview()) {
            reasons.add("AI asked for human review");
        }
        boolean needsReview = breaking
                || category.isUnknown()
                || !triggers.isEmpty()
                || answer.needsHumanReview();
        return new ClassifiedChange(
                new ChangeClassification(category, breaking, needsReview, reasons, triggers),
                aiChoseCategory ? ClassificationSource.AI : ClassificationSource.RULES,
                ai,
                suggestion
        );
    }

    /**
     * @param suggestion a category the AI proposed, to be recorded for an administrator, or null
     */
    record ClassifiedChange(
            ChangeClassification classification,
            ClassificationSource source,
            AiOutcome ai,
            CategorySuggestionDraft suggestion
    ) {
    }
}
