package com.hoangluongtran0309.releaseflow.release;

import java.time.Instant;
import java.util.UUID;

public record ReleaseSummary(
        UUID id,
        String version,
        String summary,
        ReleaseStatus status,
        long changeCount,
        Instant createdAt,
        Instant updatedAt
) {
}
