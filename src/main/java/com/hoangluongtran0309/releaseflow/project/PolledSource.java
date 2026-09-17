package com.hoangluongtran0309.releaseflow.project;

import java.time.Instant;
import java.util.UUID;

/**
 * A source whose schedule has come round, and the point in time its last poll covered.
 * It carries no credential: the poll itself asks for those when it runs.
 */
public record PolledSource(UUID id, UUID organizationId, UUID projectId, Instant pollCursorAt) {
}
