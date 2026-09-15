package com.hoangluongtran0309.releaseflow.github;

import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;

/**
 * One page of a repository's closed pull requests, most recently updated first, or why
 * it could not be read. {@code retryAfter} is set only when GitHub said how long to
 * wait before asking again.
 */
public record PullRequestListing(Status status, List<JsonNode> pullRequests, Duration retryAfter) {

    public static final int PAGE_SIZE = GitHubApiClient.PAGE_SIZE;

    public enum Status {
        LISTED,
        REJECTED,
        RATE_LIMITED,
        UNAVAILABLE,
        INVALID_RESPONSE
    }

    public PullRequestListing {
        pullRequests = pullRequests == null ? List.of() : List.copyOf(pullRequests);
    }

    static PullRequestListing listed(List<JsonNode> pullRequests) {
        return new PullRequestListing(Status.LISTED, pullRequests, null);
    }

    static PullRequestListing failed(Status status, Duration retryAfter) {
        return new PullRequestListing(status, List.of(), retryAfter);
    }

    /** A page shorter than the page size is the last one. */
    public boolean lastPage() {
        return pullRequests.size() < PAGE_SIZE;
    }
}
