package com.hoangluongtran0309.releaseflow.release;

public class DraftReleaseExistsException extends RuntimeException {

    public DraftReleaseExistsException() {
        super("This project already has a draft release. Finish or discard it first.");
    }
}
