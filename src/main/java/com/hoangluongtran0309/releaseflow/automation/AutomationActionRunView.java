package com.hoangluongtran0309.releaseflow.automation;

import java.time.Instant;
import java.util.UUID;

/** One delivery of one run, and how it ended. */
public record AutomationActionRunView(
        UUID id,
        int position,
        ActionType actionType,
        String audienceName,
        String language,
        ExecutionStatus status,
        String externalReference,
        String errorCode,
        int attempts,
        Instant startedAt,
        Instant completedAt
) {

    /** Whether this is the action a retry would carry out again. */
    public boolean retryable() {
        return status == ExecutionStatus.FAILED || status == ExecutionStatus.UNKNOWN;
    }
}
