package com.hoangluongtran0309.releaseflow.source;

import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;

/**
 * One page of a project's pull or merge requests, or why it could not be read.
 * {@code retryAfter} is set only when the provider said how long to wait before asking
 * again.
 */
public record ProviderListing(Status status, List<JsonNode> items, Duration retryAfter) {

    /** Both providers are asked for a hundred items at a time. */
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

    public static ProviderListing listed(List<JsonNode> items) {
        return new ProviderListing(Status.LISTED, items, null);
    }

    public static ProviderListing failed(Status status, Duration retryAfter) {
        return new ProviderListing(status, List.of(), retryAfter);
    }

    /** A page shorter than the page size is the last one. */
    public boolean lastPage() {
        return items.size() < PAGE_SIZE;
    }
}
