package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.github.ChangedFile;
import com.hoangluongtran0309.releaseflow.github.ChangedFileKind;
import com.hoangluongtran0309.releaseflow.github.PullRequestFiles;
import com.hoangluongtran0309.releaseflow.support.TestCategories;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Builds changes that finished processing with one ordinary source file, so the rules
 * alone decide their classification.
 */
final class ProcessedChanges {

    private static final PullRequestFiles ORDINARY_FILES = PullRequestFiles.collected(
            List.of(new ChangedFile("src/main/java/App.java", null, ChangedFileKind.MODIFIED))
    );
    private static final SensitivePaths RULES = new SensitivePathRules(List.of("**/db/migration/**")).forProject(List.of());

    private ProcessedChanges() {
    }

    static Change processed(UUID id, UUID organizationId, UUID projectId, MergedPullRequest pullRequest) {
        Change change = Change.received(id, organizationId, projectId, pullRequest, UUID.randomUUID(), Instant.now());
        change.completeProcessing(
                ORDINARY_FILES,
                ChangeAiMerge.merge(ChangeClassifier.classify(pullRequest, ORDINARY_FILES, RULES, TestCategories.CATALOG), null, null),
                Instant.now()
        );
        return change;
    }
}
