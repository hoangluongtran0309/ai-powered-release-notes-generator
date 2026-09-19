package com.hoangluongtran0309.releaseflow.automation;

/**
 * Where a Run or one of its Actions stands. {@code UNKNOWN} is not a failure: the
 * delivery may have happened, so only a person may decide to send it again.
 */
public enum ExecutionStatus {

    PENDING("Pending"),
    RUNNING("Running"),
    SUCCEEDED("Succeeded"),
    FAILED("Failed"),
    UNKNOWN("Outcome unknown"),
    CANCELLED("Cancelled");

    private final String label;

    ExecutionStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == UNKNOWN || this == CANCELLED;
    }
}
