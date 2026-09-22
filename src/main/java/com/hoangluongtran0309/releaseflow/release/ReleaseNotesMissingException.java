package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** An approved release has no audience notes to publish. */
public class ReleaseNotesMissingException extends LocalizedException {

    public ReleaseNotesMissingException() {
        super("error.release_notes_missing");
    }
}
