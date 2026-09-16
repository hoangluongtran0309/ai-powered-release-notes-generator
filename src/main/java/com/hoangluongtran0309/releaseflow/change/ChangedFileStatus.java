package com.hoangluongtran0309.releaseflow.change;

public enum ChangedFileStatus {
    COLLECTED,
    /** The files could not be listed, so no path rule could clear the change. */
    UNAVAILABLE,
    /** The source has no notion of a changed file, so the keyword scan stands in. */
    NOT_SUPPORTED
}
