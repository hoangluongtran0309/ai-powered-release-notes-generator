package com.hoangluongtran0309.releaseflow.release;

/** An audience's template could not render this release's notes, so it was not approved. */
public class ReleaseNoteRenderException extends RuntimeException {

    public ReleaseNoteRenderException(String audienceName, Throwable cause) {
        super("The template of the " + audienceName + " audience could not be rendered. Fix it, then approve again.", cause);
    }
}
