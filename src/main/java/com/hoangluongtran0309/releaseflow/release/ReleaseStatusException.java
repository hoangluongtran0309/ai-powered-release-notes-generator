package com.hoangluongtran0309.releaseflow.release;

/** The release is not in the status the requested operation needs. */
public class ReleaseStatusException extends RuntimeException {

    public ReleaseStatusException(String message) {
        super(message);
    }
}
