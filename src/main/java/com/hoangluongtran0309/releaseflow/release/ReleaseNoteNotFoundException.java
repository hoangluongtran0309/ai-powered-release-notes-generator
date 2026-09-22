package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class ReleaseNoteNotFoundException extends LocalizedException {

    public ReleaseNoteNotFoundException() {
        super("error.release_note_not_found");
    }
}
