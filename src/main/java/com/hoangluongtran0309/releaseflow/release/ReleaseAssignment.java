package com.hoangluongtran0309.releaseflow.release;

import java.util.UUID;

/** Which release of the Project a change belongs to. */
public record ReleaseAssignment(UUID changeId, UUID releaseId, String version, ReleaseStatus status) {
}
