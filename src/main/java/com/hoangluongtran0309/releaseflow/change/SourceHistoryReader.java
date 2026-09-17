package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.source.SourceCredentials;
import com.hoangluongtran0309.releaseflow.source.SourceType;

import java.time.Instant;

/**
 * Reads one page of what a source has recorded, whether an administrator asked for its
 * history or a schedule came round. Called outside any transaction; the sync decides what
 * to do with the page.
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

    /**
     * @param providerCursor what this reader last called the page, or blank to begin
     */
    HistoryPage read(
            SourceCredentials credentials,
            String token,
            String providerCursor,
            Instant windowStart,
            Instant windowEnd
    );
}
