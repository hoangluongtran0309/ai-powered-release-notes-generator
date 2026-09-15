package com.hoangluongtran0309.releaseflow.change;

import java.util.List;
import java.util.Objects;

/** How much evidence a pull request gave for its summary, from 0 to 100, and why. */
public record ContextAssessment(int score, ContextStatus status, List<String> reasons) {

    public ContextAssessment {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("A context score is between 0 and 100.");
        }
        Objects.requireNonNull(status, "status must not be null");
        reasons = List.copyOf(reasons);
    }

    public boolean insufficient() {
        return status == ContextStatus.INSUFFICIENT;
    }
}
