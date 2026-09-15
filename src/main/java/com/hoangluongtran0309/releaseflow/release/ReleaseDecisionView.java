package com.hoangluongtran0309.releaseflow.release;

import java.time.Instant;
import java.util.UUID;

public record ReleaseDecisionView(
        UUID changeId,
        ReviewAction action,
        String reviewerName,
        String note,
        Instant decidedAt
) {
}
