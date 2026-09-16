package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.category.CategoryGroup;
import com.hoangluongtran0309.releaseflow.category.CategoryRef;
import com.hoangluongtran0309.releaseflow.source.ChangedFile;
import com.hoangluongtran0309.releaseflow.source.ChangedFileKind;
import com.hoangluongtran0309.releaseflow.source.ChangedFiles;
import com.hoangluongtran0309.releaseflow.support.TestCategories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeClassifierTest {

    private static final SensitivePaths RULES =
            new SensitivePathRules(List.of("**/db/migration/**", "**/*.sql", "**/security/**")).forProject(List.of());

    @Test
    void sensitiveFileForcesReviewAndRecordsThePath() {
        ChangeClassification classification = ChangeClassifier.classify(
                pullRequest("feat: add audit table"),
                ChangedFiles.collected(List.of(
                        new ChangedFile("src/main/java/Audit.java", null, ChangedFileKind.ADDED),
                        new ChangedFile("src/main/resources/db/migration/V9__audit.sql", null, ChangedFileKind.ADDED)
                )),
                RULES,
                TestCategories.CATALOG
        );

        assertThat(classification.category()).isEqualTo(TestCategories.FEATURE);
        assertThat(classification.breaking()).isFalse();
        assertThat(classification.needsReview()).isTrue();
        assertThat(classification.triggers()).containsExactly(
                new ReviewTrigger(ReviewTriggerType.SENSITIVE_PATH, "src/main/resources/db/migration/V9__audit.sql")
        );
    }

    @Test
    void unavailableFilesForceReviewButKeepTheRuleCategory() {
        ChangeClassification classification = ChangeClassifier.classify(
                pullRequest("fix: handle empty tables"),
                ChangedFiles.unavailable(ChangedFiles.NO_ACCESS_TOKEN, false),
                RULES,
                TestCategories.CATALOG
        );

        assertThat(classification.category()).isEqualTo(TestCategories.FIX);
        assertThat(classification.needsReview()).isTrue();
        assertThat(classification.triggers())
                .containsExactly(new ReviewTrigger(ReviewTriggerType.CHANGED_FILES_UNAVAILABLE, null));
    }

    @Test
    void documentationOnlyFilesOutrankTheTitleType() {
        ChangeClassification classification = ChangeClassifier.classify(
                pullRequest("feat: describe exports", List.of("enhancement"), null),
                ChangedFiles.collected(List.of(
                        new ChangedFile("docs/exports.md", null, ChangedFileKind.ADDED),
                        new ChangedFile("README.MD", null, ChangedFileKind.MODIFIED),
                        new ChangedFile("guide/setup.adoc", "guide/install.rst", ChangedFileKind.RENAMED)
                )),
                RULES,
                TestCategories.CATALOG
        );

        assertThat(classification.category()).isEqualTo(TestCategories.DOCUMENTATION);
        assertThat(classification.needsReview()).isFalse();
        assertThat(classification.reasons()).first().isEqualTo("All changed files are documentation");
    }

    @Test
    void sensitiveDocumentationStillNeedsReview() {
        ChangeClassification classification = ChangeClassifier.classify(
                pullRequest("docs: rotate keys"),
                ChangedFiles.collected(List.of(new ChangedFile("docs/security/keys.md", null, ChangedFileKind.MODIFIED))),
                RULES,
                TestCategories.CATALOG
        );

        assertThat(classification.category()).isEqualTo(TestCategories.DOCUMENTATION);
        assertThat(classification.needsReview()).isTrue();
        assertThat(classification.triggers()).extracting(ReviewTrigger::detail).containsExactly("docs/security/keys.md");
    }

    @Test
    void anEmptyFileListIsNotDocumentationOnly() {
        ChangeClassification classification = ChangeClassifier.classify(
                pullRequest("chore: empty merge"),
                ChangedFiles.collected(List.of()),
                RULES,
                TestCategories.CATALOG
        );

        assertThat(classification.category()).isEqualTo(TestCategories.MAINTENANCE);
        assertThat(classification.needsReview()).isFalse();
        assertThat(classification.triggers()).isEmpty();
    }

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
    void classifiesConventionalCommitTitleTypes(String title, String category, String type) {
        ChangeClassification classification = classify(pullRequest(title));

        assertThat(classification.category().code()).isEqualTo(category);
        assertThat(classification.breaking()).isFalse();
        if ("UNKNOWN".equals(category)) {
            assertThat(classification.needsReview()).isTrue();
            assertThat(classification.reasons()).containsExactly("No category rule matched");
        } else {
            assertThat(classification.needsReview()).isFalse();
            assertThat(classification.reasons()).containsExactly("Title type \"" + type + "\"");
        }
    }

    @Test
    void breakingTitleMarkerKeepsCategoryAndRequiresReview() {
        ChangeClassification classification = classify(pullRequest("feat(api)!: drop v1 endpoints"));

        assertThat(classification.category()).isEqualTo(TestCategories.FEATURE);
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
        ChangeClassification classification = classify(
                pullRequest("fix: rename configuration keys", List.of(), description)
        );

        assertThat(classification.category()).isEqualTo(TestCategories.FIX);
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
        ChangeClassification classification = classify(
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
    void classifiesByLabelWhenTheTitleHasNoType(String label, String category) {
        ChangeClassification classification = classify(
                pullRequest("Improve the export flow", List.of(label, "good first issue"), null)
        );

        assertThat(classification.category().code()).isEqualTo(category);
        assertThat(classification.needsReview()).isFalse();
        assertThat(classification.reasons()).containsExactly("Label \"" + label + "\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"breaking-change", "Breaking Change", "breaking"})
    void breakingLabelsRequireReview(String label) {
        ChangeClassification classification = classify(
                pullRequest("Rework the API", List.of("enhancement", label), null)
        );

        assertThat(classification.category()).isEqualTo(TestCategories.FEATURE);
        assertThat(classification.breaking()).isTrue();
        assertThat(classification.needsReview()).isTrue();
        assertThat(classification.reasons()).containsExactly("Label \"enhancement\"", "Breaking label \"" + label + "\"");
    }

    @Test
    void titleTypeOutranksAConflictingLabelAndBothReasonsAreKept() {
        ChangeClassification classification = classify(
                pullRequest("feat: add retries", List.of("bug"), null)
        );

        assertThat(classification.category()).isEqualTo(TestCategories.FEATURE);
        assertThat(classification.needsReview()).isFalse();
        assertThat(classification.reasons()).containsExactly("Title type \"feat\"", "Label \"bug\"");
    }

    @Test
    void conflictingLabelsWithoutATitleTypeAreUnknown() {
        ChangeClassification classification = classify(
                pullRequest("Improve exports", List.of("bug", "enhancement", "docs"), null)
        );

        assertThat(classification.category()).isEqualTo(TestCategories.UNKNOWN);
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
        ChangeClassification classification = classify(
                pullRequest("Improve exports", List.of("bug", "bugfix"), null)
        );

        assertThat(classification.category()).isEqualTo(TestCategories.FIX);
        assertThat(classification.needsReview()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"feature: add export", "fixes typo in README", "feat add export", "feat:", "Revert \"feat: x\""})
    void unrecognizedChangesAreUnknownAndNeedReview(String title) {
        ChangeClassification classification = classify(
                pullRequest(title, List.of("good first issue"), "Some context.")
        );

        assertThat(classification.category()).isEqualTo(TestCategories.UNKNOWN);
        assertThat(classification.breaking()).isFalse();
        assertThat(classification.needsReview()).isTrue();
        assertThat(classification.reasons()).containsExactly("No category rule matched");
    }

    @Test
    void breakingChangeWithoutCategoryIsUnknownAndNeedsReview() {
        ChangeClassification classification = classify(
                pullRequest("Rework storage", List.of("breaking-change"), null)
        );

        assertThat(classification.category()).isEqualTo(TestCategories.UNKNOWN);
        assertThat(classification.breaking()).isTrue();
        assertThat(classification.needsReview()).isTrue();
        assertThat(classification.reasons())
                .containsExactly("Breaking label \"breaking-change\"", "No category rule matched");
    }

    @Test
    void usesTheFirstActiveCategoryOfTheGroupWhenThePreferredOneIsArchived() {
        CategoryRef bugfix = new CategoryRef("BUGFIX", "Bug fix", CategoryGroup.FIX);
        CategoryRef security = new CategoryRef("SECURITY", "Security", CategoryGroup.FIX);
        List<CategoryRef> catalog = List.of(bugfix, TestCategories.FEATURE, security, TestCategories.UNKNOWN);

        assertThat(classify(pullRequest("fix: handle empty tables"), catalog).category()).isEqualTo(bugfix);
        assertThat(classify(pullRequest("Handle empty tables", List.of("bug"), null), catalog).category()).isEqualTo(bugfix);
        assertThat(classify(pullRequest("feat: add export"), catalog).category()).isEqualTo(TestCategories.FEATURE);
    }

    @Test
    void aGroupWithoutAnActiveCategoryLocksNothing() {
        List<CategoryRef> catalog = List.of(TestCategories.FEATURE, TestCategories.UNKNOWN);

        ChangeClassification classification = classify(pullRequest("perf: cache previews"), catalog);

        assertThat(classification.category()).isEqualTo(TestCategories.UNKNOWN);
        assertThat(classification.needsReview()).isTrue();
        assertThat(classification.reasons())
                .containsExactly("Title type \"perf\"", "No active category in the Performance group");
    }

    @Test
    void anUnresolvedTitleFallsBackToLabels() {
        List<CategoryRef> catalog = List.of(TestCategories.FEATURE, TestCategories.UNKNOWN);

        ChangeClassification classification = classify(
                pullRequest("perf: cache previews", List.of("enhancement"), null), catalog);

        assertThat(classification.category()).isEqualTo(TestCategories.FEATURE);
        assertThat(classification.needsReview()).isFalse();
    }

    @Test
    void labelsResolvingToTheSameCategoryAreNotAConflict() {
        CategoryRef maintenance = new CategoryRef("CHORE", "Chore", CategoryGroup.MAINTENANCE);
        List<CategoryRef> catalog = List.of(maintenance, TestCategories.UNKNOWN);

        ChangeClassification classification = classify(
                pullRequest("Tidy up", List.of("dependencies", "refactor"), null), catalog);

        assertThat(classification.category()).isEqualTo(maintenance);
        assertThat(classification.needsReview()).isFalse();
    }

    // An ordinary source file: no sensitive path and no documentation-only rule.
    private static ChangeClassification classify(MergedPullRequest pullRequest) {
        return classify(pullRequest, TestCategories.CATALOG);
    }

    private static ChangeClassification classify(MergedPullRequest pullRequest, List<CategoryRef> catalog) {
        return ChangeClassifier.classify(
                pullRequest,
                ChangedFiles.collected(List.of(new ChangedFile("src/main/java/App.java", null, ChangedFileKind.MODIFIED))),
                RULES,
                catalog
        );
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
