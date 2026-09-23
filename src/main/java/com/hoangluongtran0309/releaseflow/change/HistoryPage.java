package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.source.ProviderListing;

import java.time.Duration;
import java.util.List;

/**
 * One page of a source's items as a sync reads it, or why it could not be read.
 *
 * @param nextProviderCursor what the provider calls the page after this one
 * @param lastPage whether the provider has nothing after this page
 * @param retryAfter set only when the provider said how long to wait
 */
record HistoryPage(
        ProviderListing.Status status,
        List<HistoryItem> items,
        String nextProviderCursor,
        boolean lastPage,
        Duration retryAfter
) {

    HistoryPage {
        items = items == null ? List.of() : List.copyOf(items);
        nextProviderCursor = nextProviderCursor == null ? "" : nextProviderCursor;
    }

    static HistoryPage listed(List<HistoryItem> items, String nextProviderCursor, boolean lastPage) {
        return new HistoryPage(ProviderListing.Status.LISTED, items, nextProviderCursor, lastPage, null);
    }

    static HistoryPage failed(ProviderListing.Status status, Duration retryAfter) {
        return new HistoryPage(status, List.of(), "", true, retryAfter);
    }
}
