package com.hoangluongtran0309.releaseflow.change;

/**
 * Why a change must be reviewed by a person regardless of its category.
 */
public enum ReviewTriggerType {
    /** A changed file matched a sensitive-path rule; the detail is the path. */
    SENSITIVE_PATH,
    /** The changed files could not be listed, so nothing could be ruled out. */
    CHANGED_FILES_UNAVAILABLE,
    /** The AI provider failed or its answer was unusable, so no AI judgement exists. */
    CLASSIFIER_FALLBACK,
    /** No catalog category fit, and the AI proposed a new one; the detail is its code. */
    CATEGORY_SUGGESTION_PENDING
}
