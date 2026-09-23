package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.gitlab.GitLabApiClient;
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
 * GitLab is asked for the merge requests updated after the window's start, least recently
 * updated first, so the list only grows at its end and an older item never appears.
 */
@Component
class GitLabHistoryReader implements SourceHistoryReader {

    private final GitLabApiClient gitLabApiClient;

    GitLabHistoryReader(GitLabApiClient gitLabApiClient) {
        this.gitLabApiClient = gitLabApiClient;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.GITLAB;
    }

    @Override
    public boolean endsAtOlderItems() {
        return false;
    }

    @Override
    public String unavailableCode() {
        return ChangedFiles.GITLAB_UNAVAILABLE;
    }

    @Override
    public HistoryPage read(
            SourceCredentials credentials,
            String token,
            String providerCursor,
            Instant windowStart,
            Instant windowEnd
    ) {
        // Both providers number their pages, and a blank cursor means the first.
        int page = providerCursor.isBlank() ? 1 : Integer.parseInt(providerCursor);
        ProviderListing listing = gitLabApiClient.mergedMergeRequests(
                credentials.apiBaseUrl(),
                credentials.externalProjectKey(),
                page,
                windowStart,
                token
        );
        if (listing.status() != ProviderListing.Status.LISTED) {
            return HistoryPage.failed(listing.status(), listing.retryAfter());
        }
        List<HistoryItem> items = new ArrayList<>();
        for (JsonNode mergeRequest : listing.items()) {
            items.add(new HistoryItem(
                    HistoryTimes.instant(mergeRequest.path("updated_at")),
                    HistoryTimes.instant(mergeRequest.path("merged_at")),
                    "!" + mergeRequest.path("iid").asString("?"),
                    HistoryTimes.readOrNull(() -> MergedMergeRequest.fromListItem(mergeRequest))
            ));
        }
        return HistoryPage.listed(items, Integer.toString(page + 1), listing.lastPage());
    }
}
