package com.hoangluongtran0309.releaseflow.change;

import java.util.ArrayList;
import java.util.List;

/**
 * Combines the fixed rules with one AI outcome (ADR-0009). The rules always win: a
 * category they chose is kept, the AI can add a breaking flag but never remove one,
 * and review triggers still force review. The AI settles a change only when the
 * rules left it Unknown and nothing else requires a person.
 */
final class ChangeAiMerge {

    private ChangeAiMerge() {
    }

    /**
     * @param ai the AI outcome, or null when no AI provider is configured
     */
    static ClassifiedChange merge(ChangeClassification rules, AiOutcome ai) {
        if (ai == null) {
            return new ClassifiedChange(rules, ClassificationSource.RULES, null);
        }
        if (!ai.succeeded()) {
            List<ReviewTrigger> triggers = new ArrayList<>(rules.triggers());
            triggers.add(ReviewTrigger.classifierFallback());
            return new ClassifiedChange(
                    new ChangeClassification(rules.category(), rules.breaking(), true, rules.reasons(), triggers),
                    ClassificationSource.RULES,
                    ai
            );
        }

        AiClassification answer = ai.classification();
        List<String> reasons = new ArrayList<>(rules.reasons());
        boolean aiChoseCategory = rules.category() == ChangeCategory.UNKNOWN && answer.category() != ChangeCategory.UNKNOWN;
        ChangeCategory category = aiChoseCategory ? answer.category() : rules.category();
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
                || category == ChangeCategory.UNKNOWN
                || !rules.triggers().isEmpty()
                || answer.needsHumanReview();
        return new ClassifiedChange(
                new ChangeClassification(category, breaking, needsReview, reasons, rules.triggers()),
                aiChoseCategory ? ClassificationSource.AI : ClassificationSource.RULES,
                ai
        );
    }

    record ClassifiedChange(ChangeClassification classification, ClassificationSource source, AiOutcome ai) {
    }
}
