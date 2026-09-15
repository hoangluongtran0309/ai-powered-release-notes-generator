package com.hoangluongtran0309.releaseflow.category;

import java.time.Instant;
import java.util.UUID;

public record CategoryView(
        UUID id,
        String code,
        String displayName,
        CategoryGroup group,
        boolean systemCategory,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
