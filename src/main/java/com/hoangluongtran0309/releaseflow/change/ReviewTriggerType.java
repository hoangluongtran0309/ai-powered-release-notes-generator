package com.hoangluongtran0309.releaseflow.change;

/**
 * Why a change must be reviewed by a person regardless of its category.
 */
public enum ReviewTriggerType {
    /** A changed file matched a sensitive-path rule; the detail is the path. */
    SENSITIVE_PATH,
    /** The changed files could not be listed, so nothing could be ruled out. */
    CHANGED_FILES_UNAVAILABLE
}
