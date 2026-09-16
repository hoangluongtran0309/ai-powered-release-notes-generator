package com.hoangluongtran0309.releaseflow.change;

/**
 * Why a change must be reviewed by a person regardless of its category.
 */
public enum ReviewTriggerType {
    /** A changed file matched a sensitive-path rule; the detail is the path. */
    SENSITIVE_PATH,
    /** The changed files could not be listed, so nothing could be ruled out. */
    CHANGED_FILES_UNAVAILABLE,
    /** A source without changed files used a risky word; the detail is that word. */
    SENSITIVE_KEYWORD,
    /** The AI provider failed or its answer was unusable, so no AI judgement exists. */
    CLASSIFIER_FALLBACK,
    /** No catalog category fit, and the AI proposed a new one; the detail is its code. */
    CATEGORY_SUGGESTION_PENDING,
    /** The pull request gave too little evidence; the detail lists the reasons. */
    CONTEXT_INSUFFICIENT,
    /** An earlier change looks the same; the detail is that change's ID. */
    DUPLICATE_CANDIDATE
}
