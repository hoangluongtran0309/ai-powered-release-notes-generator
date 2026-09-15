package com.hoangluongtran0309.releaseflow.release;

import java.time.Instant;
import java.util.UUID;

public record ReleaseSummary(
        UUID id,
        String version,
        String summary,
        ReleaseStatus status,
        long changeCount,
        long reviewedCount,
        Instant createdAt,
        Instant updatedAt,
        Instant plannedReleaseAt,
        Instant approvedAt,
        Instant publishedAt
) {
}
