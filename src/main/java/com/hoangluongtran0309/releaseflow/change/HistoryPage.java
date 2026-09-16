package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.source.ProviderListing;

import java.time.Duration;
import java.util.List;

/**
 * One page of a source's history as the import reads it, or why it could not be read.
 *
 * @param lastPage whether the provider has nothing after this page
 * @param retryAfter set only when the provider said how long to wait
 */
record HistoryPage(ProviderListing.Status status, List<HistoryItem> items, boolean lastPage, Duration retryAfter) {

    HistoryPage {
        items = items == null ? List.of() : List.copyOf(items);
    }

    static HistoryPage listed(List<HistoryItem> items, boolean lastPage) {
        return new HistoryPage(ProviderListing.Status.LISTED, items, lastPage, null);
    }

    static HistoryPage failed(ProviderListing.Status status, Duration retryAfter) {
        return new HistoryPage(status, List.of(), true, retryAfter);
    }
}
