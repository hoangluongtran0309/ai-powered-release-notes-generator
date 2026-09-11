package com.hoangluongtran0309.releaseflow.release;

public class ReleaseVersionTakenException extends RuntimeException {

    public ReleaseVersionTakenException() {
        super("This project already has a release with this version.");
    }
}
