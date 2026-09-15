package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.OutputLanguage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeAiMergeTest {

    private static final NeutralSummary SUMMARY = new NeutralSummary("Adds export.", "", "", "");

    @Test
    void withoutAiTheRulesStandAlone() {
        ChangeClassification rules = rules(ChangeCategory.FEATURE, false, List.of());

        ChangeAiMerge.ClassifiedChange merged = ChangeAiMerge.merge(rules, null);

        assertThat(merged.classification()).isEqualTo(rules);
        assertThat(merged.source()).isEqualTo(ClassificationSource.RULES);
        assertThat(merged.ai()).isNull();
    }

    @Test
    void theRulesCategoryIsNeverReplaced() {
        ChangeAiMerge.ClassifiedChange merged = ChangeAiMerge.merge(
                rules(ChangeCategory.FEATURE, false, List.of()),
                succeeded(ChangeCategory.FIX, false, false)
        );

        assertThat(merged.classification().category()).isEqualTo(ChangeCategory.FEATURE);
        assertThat(merged.classification().needsReview()).isFalse();
        assertThat(merged.source()).isEqualTo(ClassificationSource.RULES);
        assertThat(merged.classification().reasons()).containsExactly("Title type \"feat\"");
    }

    @Test
    void theAiMaySettleAChangeTheRulesLeftUnknown() {
        ChangeAiMerge.ClassifiedChange merged = ChangeAiMerge.merge(
                rules(ChangeCategory.UNKNOWN, false, List.of()),
                succeeded(ChangeCategory.FIX, false, false)
        );

        assertThat(merged.classification().category()).isEqualTo(ChangeCategory.FIX);
        assertThat(merged.classification().needsReview()).isFalse();
        assertThat(merged.source()).isEqualTo(ClassificationSource.AI);
        assertThat(merged.classification().reasons())
                .containsExactly("Title type \"feat\"", "Category from AI (OpenAI gpt-test)");
    }

    @Test
    void anUnknownAnswerStillNeedsReview() {
        ChangeAiMerge.ClassifiedChange merged = ChangeAiMerge.merge(
                rules(ChangeCategory.UNKNOWN, false, List.of()),
                succeeded(ChangeCategory.UNKNOWN, false, false)
        );

        assertThat(merged.classification().category()).isEqualTo(ChangeCategory.UNKNOWN);
        assertThat(merged.classification().needsReview()).isTrue();
        assertThat(merged.source()).isEqualTo(ClassificationSource.RULES);
    }

    @Test
    void theAiCanAddCautionButNeverRemoveIt() {
        ChangeAiMerge.ClassifiedChange aiBreaking = ChangeAiMerge.merge(
                rules(ChangeCategory.FEATURE, false, List.of()),
                succeeded(ChangeCategory.FEATURE, true, false)
        );
        assertThat(aiBreaking.classification().breaking()).isTrue();
        assertThat(aiBreaking.classification().needsReview()).isTrue();
        assertThat(aiBreaking.classification().reasons()).contains("AI marked it breaking");

        ChangeAiMerge.ClassifiedChange rulesBreaking = ChangeAiMerge.merge(
                rules(ChangeCategory.FEATURE, true, List.of()),
                succeeded(ChangeCategory.FEATURE, false, false)
        );
        assertThat(rulesBreaking.classification().breaking()).isTrue();
        assertThat(rulesBreaking.classification().needsReview()).isTrue();

        ChangeAiMerge.ClassifiedChange askedForReview = ChangeAiMerge.merge(
                rules(ChangeCategory.FEATURE, false, List.of()),
                succeeded(ChangeCategory.FEATURE, false, true)
        );
        assertThat(askedForReview.classification().needsReview()).isTrue();
        assertThat(askedForReview.classification().reasons()).contains("AI asked for human review");

        ChangeAiMerge.ClassifiedChange triggered = ChangeAiMerge.merge(
                rules(ChangeCategory.UNKNOWN, false, List.of(ReviewTrigger.sensitivePath("db/migration/V2.sql"))),
                succeeded(ChangeCategory.FIX, false, false)
        );
        assertThat(triggered.classification().needsReview()).isTrue();
        assertThat(triggered.classification().triggers()).containsExactly(ReviewTrigger.sensitivePath("db/migration/V2.sql"));
    }

    @Test
    void aFailureKeepsTheRulesAndForcesReview() {
        AiOutcome failed = new AiOutcome(AiProvider.ANTHROPIC, "claude-test", null, null, "Anthropic returned HTTP 500.");

        ChangeAiMerge.ClassifiedChange merged = ChangeAiMerge.merge(rules(ChangeCategory.FEATURE, false, List.of()), failed);

        assertThat(merged.classification().category()).isEqualTo(ChangeCategory.FEATURE);
        assertThat(merged.classification().needsReview()).isTrue();
        assertThat(merged.classification().triggers()).containsExactly(ReviewTrigger.classifierFallback());
        assertThat(merged.source()).isEqualTo(ClassificationSource.RULES);
        assertThat(merged.ai()).isSameAs(failed);
    }

    private static ChangeClassification rules(ChangeCategory category, boolean breaking, List<ReviewTrigger> triggers) {
        return new ChangeClassification(
                category,
                breaking,
                breaking || category == ChangeCategory.UNKNOWN || !triggers.isEmpty(),
                List.of("Title type \"feat\""),
                triggers
        );
    }

    private static AiOutcome succeeded(ChangeCategory category, boolean breaking, boolean needsReview) {
        return new AiOutcome(
                AiProvider.OPENAI,
                "gpt-test",
                new AiClassification(category, breaking, needsReview, SUMMARY, Map.of()),
                OutputLanguage.DEFAULT,
                null
        );
    }
}
