package com.hoangluongtran0309.releaseflow.release;

public class ReleaseNoteNotFoundException extends RuntimeException {

    public ReleaseNoteNotFoundException() {
        super("Release note was not found.");
    }
}
