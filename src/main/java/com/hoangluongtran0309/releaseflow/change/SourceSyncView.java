package com.hoangluongtran0309.releaseflow.change;

import java.time.Instant;
import java.util.UUID;

/**
 * A source and its latest sync job. Every job field is {@code null} or zero when the
 * source was never synced. A polled source is never imported by hand: its job is the
 * poll ReleaseFlow scheduled itself, and {@code nextPollAt} says when the next one is
 * due.
 */
public record SourceSyncView(
        UUID sourceId,
        String externalProjectKey,
        String deliveryMechanism,
        boolean supportsImport,
        boolean polled,
        Instant nextPollAt,
        boolean accessTokenConfigured,
        String status,
        Instant windowStart,
        Instant windowEnd,
        int scannedCount,
        int importedCount,
        int itemLimit,
        String requesterName,
        Instant startedAt,
        Instant completedAt,
        Instant nextAttemptAt,
        String lastError,
        Instant lastSyncAt,
        String lastErrorCode,
        boolean canResumeImport
) {

    /** Whether a job is queued or running, so another cannot start. */
    public boolean inProgress() {
        return "PENDING".equals(status) || "RUNNING".equals(status) || "RETRY_SCHEDULED".equals(status);
    }
}
