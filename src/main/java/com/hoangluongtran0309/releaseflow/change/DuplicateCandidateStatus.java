package com.hoangluongtran0309.releaseflow.change;

public enum DuplicateCandidateStatus {
    /** Waiting for a person. */
    OPEN,
    /** A person agreed the two changes do the same thing. Nothing is merged. */
    CONFIRMED,
    /** A person found the changes different. */
    DISMISSED
}
