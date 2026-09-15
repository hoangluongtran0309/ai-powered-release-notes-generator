package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.OutputLanguage;
import com.hoangluongtran0309.releaseflow.category.CategoryGroup;
import com.hoangluongtran0309.releaseflow.category.CategoryRef;
import com.hoangluongtran0309.releaseflow.category.CategorySuggestionDraft;
import com.hoangluongtran0309.releaseflow.support.TestCategories;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeAiMergeTest {

    private static final NeutralSummary SUMMARY = new NeutralSummary("Adds export.", "", "", "");

    @Test
    void withoutAiTheRulesStandAlone() {
        ChangeClassification rules = rules(TestCategories.FEATURE, false, List.of());

        ChangeAiMerge.ClassifiedChange merged = merge(rules, null);

        assertThat(merged.classification()).isEqualTo(rules);
        assertThat(merged.source()).isEqualTo(ClassificationSource.RULES);
        assertThat(merged.ai()).isNull();
    }

    @Test
    void aProposedCategoryKeepsTheChangeUnknownAndAddsATrigger() {
        CategorySuggestionDraft draft = new CategorySuggestionDraft("SECURITY", "Security", CategoryGroup.FIX, "None fits.");

        ChangeAiMerge.ClassifiedChange merged = merge(
                rules(TestCategories.UNKNOWN, false, List.of()),
                answer(new AiClassification(TestCategories.UNKNOWN, false, false, SUMMARY, Map.of(), draft, 90, List.of()))
        );

        assertThat(merged.classification().category()).isEqualTo(TestCategories.UNKNOWN);
        assertThat(merged.classification().needsReview()).isTrue();
        assertThat(merged.classification().triggers()).containsExactly(ReviewTrigger.categorySuggestion("SECURITY"));
        assertThat(merged.classification().reasons()).contains("AI proposed a new category \"SECURITY\"");
        assertThat(merged.suggestion()).isEqualTo(draft);
    }

    @Test
    void insufficientContextAddsOneTriggerAndForcesReview() {
        ContextAssessment context = new ContextAssessment(30, ContextStatus.INSUFFICIENT,
                List.of("NO_TICKET", "DESCRIPTION_MISSING"));

        ChangeAiMerge.ClassifiedChange merged = ChangeAiMerge.merge(
                rules(TestCategories.FEATURE, false, List.of()), succeeded(TestCategories.FEATURE, false, false), context);

        assertThat(merged.classification().needsReview()).isTrue();
        assertThat(merged.classification().triggers())
                .containsExactly(new ReviewTrigger(ReviewTriggerType.CONTEXT_INSUFFICIENT, "NO_TICKET, DESCRIPTION_MISSING"));
        assertThat(merged.context()).isEqualTo(context);
    }

    @Test
    void contextIsAssessedOnlyForASuccessfulAnswer() {
        MergedPullRequest pullRequest = new MergedPullRequest(7, "fix", null, "octocat", List.of(), "main",
                "a".repeat(40), Instant.parse("2026-09-10T09:14:22Z"), "https://github.com/acme/app/pull/7");
        ChangeClassification rules = rules(TestCategories.FIX, false, List.of());

        ChangeAiMerge.ClassifiedChange answered = ChangeAiMerge.merge(
                rules, succeeded(TestCategories.FIX, false, false), pullRequest, 60);
        ChangeAiMerge.ClassifiedChange withoutAi = ChangeAiMerge.merge(rules, null, pullRequest, 60);
        ChangeAiMerge.ClassifiedChange failed = ChangeAiMerge.merge(
                rules, new AiOutcome(AiProvider.OPENAI, "gpt-test", null, null, "OpenAI returned HTTP 500."), pullRequest, 60);

        assertThat(answered.context().score()).isEqualTo(30);
        assertThat(answered.classification().triggers()).extracting(ReviewTrigger::type)
                .containsExactly(ReviewTriggerType.CONTEXT_INSUFFICIENT);
        assertThat(withoutAi.context()).isNull();
        assertThat(withoutAi.classification().needsReview()).isFalse();
        assertThat(failed.context()).isNull();
        assertThat(failed.classification().triggers()).extracting(ReviewTrigger::type)
                .containsExactly(ReviewTriggerType.CLASSIFIER_FALLBACK);
    }

    @Test
    void aProposalIsIgnoredWhenTheRulesChoseTheCategory() {
        CategorySuggestionDraft draft = new CategorySuggestionDraft("SECURITY", "Security", CategoryGroup.FIX, "");

        ChangeAiMerge.ClassifiedChange merged = merge(
                rules(TestCategories.FEATURE, false, List.of()),
                answer(new AiClassification(TestCategories.UNKNOWN, false, false, SUMMARY, Map.of(), draft, 90, List.of()))
        );

        assertThat(merged.classification().category()).isEqualTo(TestCategories.FEATURE);
        assertThat(merged.classification().triggers()).isEmpty();
        assertThat(merged.suggestion()).isNull();
    }

    @Test
    void theRulesCategoryIsNeverReplaced() {
        ChangeAiMerge.ClassifiedChange merged = merge(
                rules(TestCategories.FEATURE, false, List.of()),
                succeeded(TestCategories.FIX, false, false)
        );

        assertThat(merged.classification().category()).isEqualTo(TestCategories.FEATURE);
        assertThat(merged.classification().needsReview()).isFalse();
        assertThat(merged.source()).isEqualTo(ClassificationSource.RULES);
        assertThat(merged.classification().reasons()).containsExactly("Title type \"feat\"");
    }

    @Test
    void theAiMaySettleAChangeTheRulesLeftUnknown() {
        ChangeAiMerge.ClassifiedChange merged = merge(
                rules(TestCategories.UNKNOWN, false, List.of()),
                succeeded(TestCategories.FIX, false, false)
        );

        assertThat(merged.classification().category()).isEqualTo(TestCategories.FIX);
        assertThat(merged.classification().needsReview()).isFalse();
        assertThat(merged.source()).isEqualTo(ClassificationSource.AI);
        assertThat(merged.classification().reasons())
                .containsExactly("Title type \"feat\"", "Category from AI (OpenAI gpt-test)");
    }

    @Test
    void anUnknownAnswerStillNeedsReview() {
        ChangeAiMerge.ClassifiedChange merged = merge(
                rules(TestCategories.UNKNOWN, false, List.of()),
                succeeded(TestCategories.UNKNOWN, false, false)
        );

        assertThat(merged.classification().category()).isEqualTo(TestCategories.UNKNOWN);
        assertThat(merged.classification().needsReview()).isTrue();
        assertThat(merged.source()).isEqualTo(ClassificationSource.RULES);
    }

    @Test
    void theAiCanAddCautionButNeverRemoveIt() {
        ChangeAiMerge.ClassifiedChange aiBreaking = merge(
                rules(TestCategories.FEATURE, false, List.of()),
                succeeded(TestCategories.FEATURE, true, false)
        );
        assertThat(aiBreaking.classification().breaking()).isTrue();
        assertThat(aiBreaking.classification().needsReview()).isTrue();
        assertThat(aiBreaking.classification().reasons()).contains("AI marked it breaking");

        ChangeAiMerge.ClassifiedChange rulesBreaking = merge(
                rules(TestCategories.FEATURE, true, List.of()),
                succeeded(TestCategories.FEATURE, false, false)
        );
        assertThat(rulesBreaking.classification().breaking()).isTrue();
        assertThat(rulesBreaking.classification().needsReview()).isTrue();

        ChangeAiMerge.ClassifiedChange askedForReview = merge(
                rules(TestCategories.FEATURE, false, List.of()),
                succeeded(TestCategories.FEATURE, false, true)
        );
        assertThat(askedForReview.classification().needsReview()).isTrue();
        assertThat(askedForReview.classification().reasons()).contains("AI asked for human review");

        ChangeAiMerge.ClassifiedChange triggered = merge(
                rules(TestCategories.UNKNOWN, false, List.of(ReviewTrigger.sensitivePath("db/migration/V2.sql"))),
                succeeded(TestCategories.FIX, false, false)
        );
        assertThat(triggered.classification().needsReview()).isTrue();
        assertThat(triggered.classification().triggers()).containsExactly(ReviewTrigger.sensitivePath("db/migration/V2.sql"));
    }

    @Test
    void aFailureKeepsTheRulesAndForcesReview() {
        AiOutcome failed = new AiOutcome(AiProvider.ANTHROPIC, "claude-test", null, null, "Anthropic returned HTTP 500.");

        ChangeAiMerge.ClassifiedChange merged = merge(rules(TestCategories.FEATURE, false, List.of()), failed);

        assertThat(merged.classification().category()).isEqualTo(TestCategories.FEATURE);
        assertThat(merged.classification().needsReview()).isTrue();
        assertThat(merged.classification().triggers()).containsExactly(ReviewTrigger.classifierFallback());
        assertThat(merged.source()).isEqualTo(ClassificationSource.RULES);
        assertThat(merged.ai()).isSameAs(failed);
    }

    private static ChangeClassification rules(CategoryRef category, boolean breaking, List<ReviewTrigger> triggers) {
        return new ChangeClassification(
                category,
                breaking,
                breaking || category.isUnknown() || !triggers.isEmpty(),
                List.of("Title type \"feat\""),
                triggers
        );
    }

    private static AiOutcome succeeded(CategoryRef category, boolean breaking, boolean needsReview) {
        return answer(new AiClassification(category, breaking, needsReview, SUMMARY, Map.of(), null, 90, List.of()));
    }

    private static ChangeAiMerge.ClassifiedChange merge(ChangeClassification rules, AiOutcome ai) {
        return ChangeAiMerge.merge(rules, ai, null);
    }

    private static AiOutcome answer(AiClassification classification) {
        return new AiOutcome(AiProvider.OPENAI, "gpt-test", classification, OutputLanguage.DEFAULT, null);
    }
}
