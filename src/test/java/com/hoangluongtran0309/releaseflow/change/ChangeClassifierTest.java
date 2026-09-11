package com.hoangluongtran0309.releaseflow.change;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeClassifierTest {

    @ParameterizedTest
    @CsvSource({
            "'feat: add CSV export', FEATURE, feat",
            "'fix: handle empty tables', FIX, fix",
            "'perf: cache rendered notes', PERFORMANCE, perf",
            "'docs: explain webhook setup', DOCUMENTATION, docs",
            "'refactor: extract classifier', MAINTENANCE, refactor",
            "'chore: bump version', MAINTENANCE, chore",
            "'ci: run verify on pushes', MAINTENANCE, ci",
            "'build: pin node', MAINTENANCE, build",
            "'test: cover tenant isolation', MAINTENANCE, test",
            "'feat(ui): add inbox', FEATURE, feat",
            "'Fix(api)  :  trim input', UNKNOWN, ",
            "'FIX(api): trim input', FIX, fix",
            "'  docs(readme): typo', DOCUMENTATION, docs"
    })
    void classifiesConventionalCommitTitleTypes(String title, ChangeCategory category, String type) {
        ChangeClassification classification = ChangeClassifier.classify(pullRequest(title));

        assertThat(classification.category()).isEqualTo(category);
        assertThat(classification.breaking()).isFalse();
        if (category == ChangeCategory.UNKNOWN) {
            assertThat(classification.needsReview()).isTrue();
            assertThat(classification.reasons()).containsExactly("No category rule matched");
        } else {
            assertThat(classification.needsReview()).isFalse();
            assertThat(classification.reasons()).containsExactly("Title type \"" + type + "\"");
        }
    }

    @Test
    void breakingTitleMarkerKeepsCategoryAndRequiresReview() {
        ChangeClassification classification = ChangeClassifier.classify(pullRequest("feat(api)!: drop v1 endpoints"));

        assertThat(classification.category()).isEqualTo(ChangeCategory.FEATURE);
        assertThat(classification.breaking()).isTrue();
        assertThat(classification.needsReview()).isTrue();
        assertThat(classification.reasons()).containsExactly("Title type \"feat\"", "Title breaking marker \"!\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Removes the legacy exporter.\n\nBREAKING CHANGE: the v1 export endpoint is gone.",
            "BREAKING-CHANGE: configuration keys were renamed."
    })
    void breakingChangeFooterRequiresReview(String description) {
        ChangeClassification classification = ChangeClassifier.classify(
                pullRequest("fix: rename configuration keys", List.of(), description)
        );

        assertThat(classification.category()).isEqualTo(ChangeCategory.FIX);
        assertThat(classification.breaking()).isTrue();
        assertThat(classification.needsReview()).isTrue();
        assertThat(classification.reasons()).containsExactly("Title type \"fix\"", "BREAKING CHANGE footer");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "This is not a breaking change: callers keep working.",
            "Mentions BREAKING CHANGE: only inside a sentence.",
            "breaking change: lowercase footer tokens do not count."
    })
    void ignoresBreakingWordsOutsideTheFooterToken(String description) {
        ChangeClassification classification = ChangeClassifier.classify(
                pullRequest("feat: add export", List.of(), description)
        );

        assertThat(classification.breaking()).isFalse();
        assertThat(classification.needsReview()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "enhancement, FEATURE",
            "Feature, FEATURE",
            "bug, FIX",
            "bugfix, FIX",
            "performance, PERFORMANCE",
            "documentation, DOCUMENTATION",
            "docs, DOCUMENTATION",
            "dependencies, MAINTENANCE",
            "maintenance, MAINTENANCE",
            "chore, MAINTENANCE",
            "refactor, MAINTENANCE"
    })
    void classifiesByLabelWhenTheTitleHasNoType(String label, ChangeCategory category) {
        ChangeClassification classification = ChangeClassifier.classify(
                pullRequest("Improve the export flow", List.of(label, "good first issue"), null)
        );

        assertThat(classification.category()).isEqualTo(category);
        assertThat(classification.needsReview()).isFalse();
        assertThat(classification.reasons()).containsExactly("Label \"" + label + "\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"breaking-change", "Breaking Change", "breaking"})
    void breakingLabelsRequireReview(String label) {
        ChangeClassification classification = ChangeClassifier.classify(
                pullRequest("Rework the API", List.of("enhancement", label), null)
        );

        assertThat(classification.category()).isEqualTo(ChangeCategory.FEATURE);
        assertThat(classification.breaking()).isTrue();
        assertThat(classification.needsReview()).isTrue();
        assertThat(classification.reasons()).containsExactly("Label \"enhancement\"", "Breaking label \"" + label + "\"");
    }

    @Test
    void titleTypeOutranksAConflictingLabelAndBothReasonsAreKept() {
        ChangeClassification classification = ChangeClassifier.classify(
                pullRequest("feat: add retries", List.of("bug"), null)
        );

        assertThat(classification.category()).isEqualTo(ChangeCategory.FEATURE);
        assertThat(classification.needsReview()).isFalse();
        assertThat(classification.reasons()).containsExactly("Title type \"feat\"", "Label \"bug\"");
    }

    @Test
    void conflictingLabelsWithoutATitleTypeAreUnknown() {
        ChangeClassification classification = ChangeClassifier.classify(
                pullRequest("Improve exports", List.of("bug", "enhancement", "docs"), null)
        );

        assertThat(classification.category()).isEqualTo(ChangeCategory.UNKNOWN);
        assertThat(classification.needsReview()).isTrue();
        assertThat(classification.reasons()).containsExactly(
                "Label \"bug\"",
                "Label \"enhancement\"",
                "Label \"docs\"",
                "Conflicting labels \"bug\", \"enhancement\", \"docs\""
        );
    }

    @Test
    void labelsNamingTheSameCategoryAreNotAConflict() {
        ChangeClassification classification = ChangeClassifier.classify(
                pullRequest("Improve exports", List.of("bug", "bugfix"), null)
        );

        assertThat(classification.category()).isEqualTo(ChangeCategory.FIX);
        assertThat(classification.needsReview()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"feature: add export", "fixes typo in README", "feat add export", "feat:", "Revert \"feat: x\""})
    void unrecognizedChangesAreUnknownAndNeedReview(String title) {
        ChangeClassification classification = ChangeClassifier.classify(
                pullRequest(title, List.of("good first issue"), "Some context.")
        );

        assertThat(classification.category()).isEqualTo(ChangeCategory.UNKNOWN);
        assertThat(classification.breaking()).isFalse();
        assertThat(classification.needsReview()).isTrue();
        assertThat(classification.reasons()).containsExactly("No category rule matched");
    }

    @Test
    void breakingChangeWithoutCategoryIsUnknownAndNeedsReview() {
        ChangeClassification classification = ChangeClassifier.classify(
                pullRequest("Rework storage", List.of("breaking-change"), null)
        );

        assertThat(classification.category()).isEqualTo(ChangeCategory.UNKNOWN);
        assertThat(classification.breaking()).isTrue();
        assertThat(classification.needsReview()).isTrue();
        assertThat(classification.reasons())
                .containsExactly("Breaking label \"breaking-change\"", "No category rule matched");
    }

    private static MergedPullRequest pullRequest(String title) {
        return pullRequest(title, List.of(), null);
    }

    private static MergedPullRequest pullRequest(String title, List<String> labels, String description) {
        return new MergedPullRequest(
                7,
                title,
                description,
                "octocat",
                labels,
                "main",
                "a".repeat(40),
                Instant.parse("2026-09-10T09:14:22Z"),
                "https://github.com/acme/releaseflow/pull/7"
        );
    }
}
