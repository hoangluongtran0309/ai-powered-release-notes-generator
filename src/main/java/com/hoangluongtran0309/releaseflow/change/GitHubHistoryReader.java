package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.github.GitHubApiClient;
import com.hoangluongtran0309.releaseflow.source.ChangedFiles;
import com.hoangluongtran0309.releaseflow.source.ProviderListing;
import com.hoangluongtran0309.releaseflow.source.SourceCredentials;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * GitHub lists a repository's closed pull requests most recently updated first, so the
 * first one updated before the window means there is nothing older left to read.
 */
@Component
class GitHubHistoryReader implements SourceHistoryReader {

    private final GitHubApiClient gitHubApiClient;

    GitHubHistoryReader(GitHubApiClient gitHubApiClient) {
        this.gitHubApiClient = gitHubApiClient;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.GITHUB;
    }

    @Override
    public boolean endsAtOlderItems() {
        return true;
    }

    @Override
    public String unavailableCode() {
        return ChangedFiles.GITHUB_UNAVAILABLE;
    }

    @Override
    public HistoryPage read(
            SourceCredentials credentials,
            String token,
            ImportCursor cursor,
            Instant windowStart,
            Instant windowEnd
    ) {
        ProviderListing listing = gitHubApiClient.closedPullRequests(
                credentials.repositoryOwner(),
                credentials.repositoryName(),
                cursor.page(),
                token
        );
        if (listing.status() != ProviderListing.Status.LISTED) {
            return HistoryPage.failed(listing.status(), listing.retryAfter());
        }
        List<HistoryItem> items = new ArrayList<>();
        for (JsonNode pullRequest : listing.items()) {
            items.add(new HistoryItem(
                    HistoryTimes.instant(pullRequest.path("updated_at")),
                    HistoryTimes.instant(pullRequest.path("merged_at")),
                    "#" + pullRequest.path("number").asString("?"),
                    HistoryTimes.readOrNull(() -> MergedPullRequest.from(pullRequest))
            ));
        }
        return HistoryPage.listed(items, listing.lastPage());
    }
}
