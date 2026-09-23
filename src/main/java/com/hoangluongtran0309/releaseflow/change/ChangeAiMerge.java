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
 * administrator decides the proposal. Insufficient context adds a trigger too; context
 * is assessed only when the AI answered.
 */
final class ChangeAiMerge {

    private ChangeAiMerge() {
    }

    /**
     * @param ai the AI outcome, or null when no AI provider is configured
     * @param context the assessment of a successful answer, or null
     */
    static ClassifiedChange merge(ChangeClassification rules, AiOutcome ai, ContextAssessment context) {
        if (ai == null) {
            return new ClassifiedChange(rules, ClassificationSource.RULES, null, null, null);
        }
        if (!ai.succeeded()) {
            List<ReviewTrigger> triggers = new ArrayList<>(rules.triggers());
            triggers.add(ReviewTrigger.classifierFallback());
            return new ClassifiedChange(
                    new ChangeClassification(rules.category(), rules.breaking(), true, rules.reasons(), triggers),
                    ClassificationSource.RULES,
                    ai,
                    null,
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
        if (context != null && context.insufficient()) {
            triggers.add(ReviewTrigger.contextInsufficient(context.reasons()));
            reasons.add("Context score " + context.score() + " is below the threshold");
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
                suggestion,
                context
        );
    }

    /** Merges an outcome, assessing the context of a successful answer against the pull request. */
    static ClassifiedChange merge(
            ChangeClassification rules,
            AiOutcome ai,
            MergedPullRequest pullRequest,
            int contextThreshold
    ) {
        ContextAssessment context = ai != null && ai.succeeded()
                ? ContextSufficiency.assess(
                        pullRequest,
                        ai.classification().contextScore(),
                        ai.classification().contextReasons(),
                        contextThreshold
                )
                : null;
        return merge(rules, ai, context);
    }

    /**
     * @param suggestion a category the AI proposed, to be recorded for an administrator, or null
     * @param context the context assessment of a successful answer, or null
     */
    record ClassifiedChange(
            ChangeClassification classification,
            ClassificationSource source,
            AiOutcome ai,
            CategorySuggestionDraft suggestion,
            ContextAssessment context
    ) {
    }
}
