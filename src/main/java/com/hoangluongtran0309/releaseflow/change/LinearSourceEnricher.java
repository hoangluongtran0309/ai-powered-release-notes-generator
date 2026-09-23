package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.linear.LinearApiClient;
import com.hoangluongtran0309.releaseflow.linear.LinearIssue;
import com.hoangluongtran0309.releaseflow.source.ChangedFiles;
import com.hoangluongtran0309.releaseflow.source.SourceCredentials;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Linear has no files to list. What it can do is restate the issue, which also proves the
 * issue really belongs to the connected team and workspace, so a delivery that named
 * someone else's issue adds nothing.
 */
@Component
class LinearSourceEnricher implements SourceEnricher {

    private final LinearApiClient linearApiClient;

    LinearSourceEnricher(LinearApiClient linearApiClient) {
        this.linearApiClient = linearApiClient;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.LINEAR;
    }

    @Override
    public Enrichment enrich(SourceCredentials credentials, String token, MergedPullRequest change) {
        Optional<LinearIssue> issue = linearApiClient.issue(
                change.externalId(),
                credentials.externalProjectKey(),
                credentials.externalWorkspaceKey(),
                token
        );
        if (issue.isEmpty()) {
            // Keep what the delivery carried, and ask again before settling for it.
            return Enrichment.retryable(ChangedFiles.notSupported(), change);
        }
        return Enrichment.of(ChangedFiles.notSupported(), restated(change, issue.get()));
    }

    private static MergedPullRequest restated(MergedPullRequest change, LinearIssue issue) {
        return new MergedPullRequest(
                change.externalId(),
                change.number(),
                issue.title().isBlank() ? change.title() : issue.title(),
                issue.description().isBlank() ? change.description() : issue.description(),
                issue.authorName().isBlank() ? change.authorLogin() : issue.authorName(),
                change.labels(),
                null,
                null,
                change.mergedAt(),
                issue.url().isBlank() ? change.url() : issue.url()
        );
    }
}
