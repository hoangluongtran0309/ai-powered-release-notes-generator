package com.hoangluongtran0309.releaseflow.change;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A Project's sensitive paths: the deployment's baseline, what administrators added, and
 * the patterns new changes are checked against.
 */
public record SensitivePathsView(
        UUID projectId,
        List<String> baseline,
        List<String> additions,
        List<String> effective,
        String updatedBy,
        Instant updatedAt
) {
}
