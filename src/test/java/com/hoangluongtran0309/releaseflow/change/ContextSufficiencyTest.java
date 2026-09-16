package com.hoangluongtran0309.releaseflow.change;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextSufficiencyTest {

    private static final int THRESHOLD = 60;
    private static final String DESCRIPTION = "Exports stream rows so large tables no longer time out.";

    @Test
    void keepsTheAiScoreWhenThePullRequestSaysEnough() {
        ContextAssessment assessment = assess("Stream large table exports", DESCRIPTION, 85, List.of());

        assertThat(assessment).isEqualTo(new ContextAssessment(85, ContextStatus.SUFFICIENT, List.of()));
    }

    @Test
    void capsAMissingDescriptionAt30() {
        ContextAssessment assessment = assess("Add account recovery", "   ", 95, List.of("NO_TICKET"));

        assertThat(assessment.score()).isEqualTo(30);
        assertThat(assessment.status()).isEqualTo(ContextStatus.INSUFFICIENT);
        assertThat(assessment.reasons()).containsExactly("NO_TICKET", "DESCRIPTION_MISSING");
    }

    @Test
    void capsAShortTitleAt50() {
        assertThat(assess("Fix login", DESCRIPTION, 90, List.of()))
                .isEqualTo(new ContextAssessment(50, ContextStatus.INSUFFICIENT, List.of("TITLE_TOO_SHORT")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fix", " WIP ", "Cleanup", "misc", "update", "change"})
    void capsAGenericTitleWithAShortDescriptionAt40(String title) {
        ContextAssessment assessment = assess(title, "Small tweak.", 90, List.of());

        assertThat(assessment.score()).isEqualTo(40);
        assertThat(assessment.reasons()).containsExactly("TITLE_TOO_SHORT", "GENERIC_TITLE");
    }

    @Test
    void aGenericTitleWithALongDescriptionIsOnlyShort() {
        ContextAssessment assessment = assess("fix", "x".repeat(160), 90, List.of());

        assertThat(assessment.score()).isEqualTo(50);
        assertThat(assessment.reasons()).containsExactly("TITLE_TOO_SHORT");
    }

    @Test
    void neverRaisesTheAiScoreAndKeepsReasonsOnce() {
        ContextAssessment assessment = assess("Stream large table exports", null, 12, List.of("DESCRIPTION_MISSING"));

        assertThat(assessment.score()).isEqualTo(12);
        assertThat(assessment.reasons()).containsExactly("DESCRIPTION_MISSING");
    }

    @Test
    void theThresholdItselfIsSufficient() {
        assertThat(assess("Stream large table exports", DESCRIPTION, 60, List.of()).status()).isEqualTo(ContextStatus.SUFFICIENT);
        assertThat(assess("Stream large table exports", DESCRIPTION, 59, List.of()).status()).isEqualTo(ContextStatus.INSUFFICIENT);
    }

    private static ContextAssessment assess(String title, String description, int score, List<String> reasons) {
        MergedPullRequest pullRequest = new MergedPullRequest("7", 7, title, description, "octocat", List.of(), "main",
                "a".repeat(40), Instant.parse("2026-09-10T09:14:22Z"), "https://github.com/acme/app/pull/7");
        return ContextSufficiency.assess(pullRequest, score, reasons, THRESHOLD);
    }
}
