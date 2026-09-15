package com.hoangluongtran0309.releaseflow.audience;

import java.time.Instant;
import java.util.UUID;

public record AudienceView(
        UUID id,
        String code,
        String displayName,
        String communicationIntent,
        String templateBody,
        boolean preset,
        Instant createdAt,
        Instant updatedAt
) {
}
