package com.hoangluongtran0309.releaseflow.change;

public class InvalidChangeReviewException extends RuntimeException {

    public InvalidChangeReviewException() {
        super("Choose an active category of the catalog. A reviewed change cannot stay Unknown.");
    }
}
