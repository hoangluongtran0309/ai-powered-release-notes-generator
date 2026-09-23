package com.hoangluongtran0309.releaseflow.source;

import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;

/**
 * One page of a project's items, or why it could not be read. A provider either numbers
 * its pages, in which case a short page is the last one, or hands back a token for the
 * next page, in which case the absence of a token is what ends the list.
 * {@code retryAfter} is set only when the provider said how long to wait before asking
 * again.
 */
public record ProviderListing(Status status, List<JsonNode> items, String nextPageToken, Duration retryAfter) {

    /** Every provider is asked for a hundred items at a time. */
    public static final int PAGE_SIZE = 100;

    public enum Status {
        LISTED,
        REJECTED,
        RATE_LIMITED,
        UNAVAILABLE,
        INVALID_RESPONSE
    }

    public ProviderListing {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** A page from a provider that numbers its pages. */
    public static ProviderListing listed(List<JsonNode> items) {
        return new ProviderListing(Status.LISTED, items, null, null);
    }

    /** A page from a provider that hands back a token for the next one. */
    public static ProviderListing paged(List<JsonNode> items, String nextPageToken) {
        return new ProviderListing(Status.LISTED, items, nextPageToken == null ? "" : nextPageToken, null);
    }

    public static ProviderListing failed(Status status, Duration retryAfter) {
        return new ProviderListing(status, List.of(), null, retryAfter);
    }

    /** For a numbered provider, a short page is the last; for a token one, no token is. */
    public boolean lastPage() {
        return nextPageToken == null ? items.size() < PAGE_SIZE : nextPageToken.isBlank();
    }
}
