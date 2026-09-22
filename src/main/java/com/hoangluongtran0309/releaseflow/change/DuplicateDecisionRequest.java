package com.hoangluongtran0309.releaseflow.change;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public class DuplicateDecisionRequest {

    @NotNull(message = "{validation.duplicateDecision.required}")
    private DuplicateCandidateStatus decision;

    public DuplicateCandidateStatus getDecision() {
        return decision;
    }

    public void setDecision(DuplicateCandidateStatus decision) {
        this.decision = decision;
    }

    @AssertTrue(message = "{validation.duplicateDecision.required}")
    public boolean isDecisionFinal() {
        return decision != DuplicateCandidateStatus.OPEN;
    }
}
