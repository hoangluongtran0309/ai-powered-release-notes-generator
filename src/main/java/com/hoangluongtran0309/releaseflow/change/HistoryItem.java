package com.hoangluongtran0309.releaseflow.change;

import java.time.Instant;

/**
 * One pull or merge request the import scanned.
 *
 * @param updatedAt when the provider last saw it change, or null when it did not say
 * @param mergedAt when it was merged, or null when it was not
 * @param label how to name it in a log line
 * @param change the normalized change, or null when the provider's item could not be read
 */
record HistoryItem(Instant updatedAt, Instant mergedAt, String label, MergedPullRequest change) {
}
