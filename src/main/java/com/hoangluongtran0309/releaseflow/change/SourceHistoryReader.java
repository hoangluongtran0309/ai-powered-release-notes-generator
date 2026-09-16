package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.source.SourceCredentials;
import com.hoangluongtran0309.releaseflow.source.SourceType;

import java.time.Instant;

/**
 * Reads one page of a source's merged pull or merge requests. Called outside any
 * transaction; the import decides what to do with the page.
 */
interface SourceHistoryReader {

    SourceType sourceType();

    /**
     * Whether an item the provider updated before the window means there is nothing older
     * left to find. It is true only for a provider asked for its newest updates first.
     */
    boolean endsAtOlderItems();

    /** The short, stable code that says this provider could not be reached. */
    String unavailableCode();

    HistoryPage read(
            SourceCredentials credentials,
            String token,
            ImportCursor cursor,
            Instant windowStart,
            Instant windowEnd
    );
}
