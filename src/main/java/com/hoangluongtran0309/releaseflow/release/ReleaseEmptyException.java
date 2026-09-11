package com.hoangluongtran0309.releaseflow.release;

public class ReleaseEmptyException extends RuntimeException {

    public ReleaseEmptyException() {
        super("Add at least one change before publishing this release.");
    }
}
