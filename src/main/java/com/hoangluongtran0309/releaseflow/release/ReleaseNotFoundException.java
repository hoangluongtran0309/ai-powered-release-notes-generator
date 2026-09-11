package com.hoangluongtran0309.releaseflow.release;

public class ReleaseNotFoundException extends RuntimeException {

    public ReleaseNotFoundException() {
        super("Release was not found.");
    }
}
