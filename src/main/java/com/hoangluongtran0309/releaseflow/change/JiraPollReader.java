package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.jira.JiraAdfText;
import com.hoangluongtran0309.releaseflow.jira.JiraApiClient;
import com.hoangluongtran0309.releaseflow.source.ProviderListing;
import com.hoangluongtran0309.releaseflow.source.SourceCredentials;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Jira is asked for the issues that are done and were updated after the window's start,
 * least recently updated first, so the list only grows at its end. It pages with an opaque
 * token rather than a page number.
 */
@Component
class JiraPollReader implements SourceHistoryReader {

    static final String UNAVAILABLE = "jira_unavailable";

    private final JiraApiClient jiraApiClient;
    private final int descriptionLimit;

    JiraPollReader(
            JiraApiClient jiraApiClient,
            @Value("${releaseflow.jira.description-max-characters}") int descriptionLimit
    ) {
        this.jiraApiClient = jiraApiClient;
        this.descriptionLimit = descriptionLimit;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.JIRA;
    }

    @Override
    public boolean endsAtOlderItems() {
        return false;
    }

    @Override
    public String unavailableCode() {
        return UNAVAILABLE;
    }

    @Override
    public HistoryPage read(
            SourceCredentials credentials,
            String token,
            String providerCursor,
            Instant windowStart,
            Instant windowEnd
    ) {
        ProviderListing listing = jiraApiClient.completedIssues(
                credentials.apiBaseUrl(),
                credentials.externalProjectKey(),
                credentials.credentialIdentity(),
                token,
                windowStart,
                providerCursor
        );
        if (listing.status() != ProviderListing.Status.LISTED) {
            return HistoryPage.failed(listing.status(), listing.retryAfter());
        }
        List<HistoryItem> items = new ArrayList<>();
        for (JsonNode issue : listing.items()) {
            String key = issue.path("key").asString("?");
            items.add(new HistoryItem(
                    // Jira orders by update time, but the window is judged on when it was done.
                    completedAt(issue),
                    completedAt(issue),
                    key,
                    HistoryTimes.readOrNull(() -> CompletedJiraIssue.from(
                            issue,
                            credentials.apiBaseUrl(),
                            JiraAdfText.convert(issue.path("fields").path("description"), descriptionLimit)
                    ))
            ));
        }
        return HistoryPage.listed(items, listing.nextPageToken(), listing.lastPage());
    }

    private static Instant completedAt(JsonNode issue) {
        try {
            return CompletedJiraIssue.completedAt(issue.path("fields"));
        } catch (MalformedWebhookPayloadException exception) {
            return null;
        }
    }
}
