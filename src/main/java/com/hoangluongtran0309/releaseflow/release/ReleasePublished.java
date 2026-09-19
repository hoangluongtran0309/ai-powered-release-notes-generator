package com.hoangluongtran0309.releaseflow.release;

import java.time.Instant;
import java.util.UUID;

/**
 * A release became a published, frozen snapshot. Published inside the transaction
 * that publishes it, so a listener may record work to do but must never do it here:
 * anything that throws would undo the publication itself.
 */
public record ReleasePublished(
        UUID organizationId,
        UUID projectId,
        UUID releaseId,
        String version,
        UUID publishedBy,
        Instant publishedAt
) {
}
