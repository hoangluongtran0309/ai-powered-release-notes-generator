package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** One audience's template could not produce a note, so the release stays unapproved. */
public class ReleaseNoteRenderException extends LocalizedException {

    public ReleaseNoteRenderException(String audienceName, Throwable cause) {
        super("error.release_note_render_failed", cause, audienceName);
    }
}
