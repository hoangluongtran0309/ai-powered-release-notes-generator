package com.hoangluongtran0309.releaseflow.automation;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Asks for one run of a rule by hand. The request ID makes a repeat the same run
 * rather than a second delivery, so a retried click never sends the note twice.
 */
public class ExecuteRuleRequest {

    @NotNull(message = "{validation.release.required}")
    private UUID releaseId;

    private UUID requestId;

    public UUID getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(UUID releaseId) {
        this.releaseId = releaseId;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public void setRequestId(UUID requestId) {
        this.requestId = requestId;
    }
}
