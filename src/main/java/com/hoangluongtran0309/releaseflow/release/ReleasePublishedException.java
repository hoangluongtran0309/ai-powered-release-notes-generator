package com.hoangluongtran0309.releaseflow.release;

public class ReleasePublishedException extends RuntimeException {

    public ReleasePublishedException() {
        super("This release is published. Its release note is an immutable snapshot and cannot change.");
    }
}
