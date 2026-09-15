package com.hoangluongtran0309.releaseflow.project;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProjectView(
        UUID id,
        String name,
        Instant createdAt,
        List<IntegrationSourceView> sources
) {
}
