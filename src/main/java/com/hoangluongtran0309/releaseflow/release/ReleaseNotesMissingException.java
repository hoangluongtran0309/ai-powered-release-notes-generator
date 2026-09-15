package com.hoangluongtran0309.releaseflow.release;

/** An approved release has no audience notes to publish. */
public class ReleaseNotesMissingException extends RuntimeException {

    public ReleaseNotesMissingException() {
        super("This release has no release notes. Return it to draft and approve it again to write them.");
    }
}
