package com.hoangluongtran0309.releaseflow.release;

public class ReleaseReviewIncompleteException extends RuntimeException {

    public ReleaseReviewIncompleteException() {
        super("Record a decision for every change of this release before approving it.");
    }
}
