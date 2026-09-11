package com.hoangluongtran0309.releaseflow.change;

public class InvalidChangeReviewException extends RuntimeException {

    public InvalidChangeReviewException() {
        super("Choose one of: feature, fix, performance, documentation, maintenance. A reviewed change cannot stay Unknown.");
    }
}
