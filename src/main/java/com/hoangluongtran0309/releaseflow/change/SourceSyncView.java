package com.hoangluongtran0309.releaseflow.change;

import java.time.Instant;
import java.util.UUID;

/**
 * A source and the latest import of its history. Every import field is {@code null} or
 * zero when the source was never imported.
 */
public record SourceSyncView(
        UUID sourceId,
        String externalProjectKey,
        String deliveryMechanism,
        boolean supportsImport,
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

    /** Whether an import is queued or running, so another cannot start. */
    public boolean inProgress() {
        return "PENDING".equals(status) || "RUNNING".equals(status) || "RETRY_SCHEDULED".equals(status);
    }
}
