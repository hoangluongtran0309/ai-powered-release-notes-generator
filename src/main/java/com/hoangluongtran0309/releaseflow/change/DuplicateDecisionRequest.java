package com.hoangluongtran0309.releaseflow.change;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public class DuplicateDecisionRequest {

    @NotNull(message = "Choose CONFIRMED or DISMISSED.")
    private DuplicateCandidateStatus decision;

    public DuplicateCandidateStatus getDecision() {
        return decision;
    }

    public void setDecision(DuplicateCandidateStatus decision) {
        this.decision = decision;
    }

    @AssertTrue(message = "Choose CONFIRMED or DISMISSED.")
    public boolean isDecisionFinal() {
        return decision != DuplicateCandidateStatus.OPEN;
    }
}
