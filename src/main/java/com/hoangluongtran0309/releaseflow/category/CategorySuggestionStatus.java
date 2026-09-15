package com.hoangluongtran0309.releaseflow.category;

public enum CategorySuggestionStatus {
    /** Waiting for an administrator. */
    PENDING_REVIEW,
    /** The proposed category was added to the catalog. */
    APPROVED,
    /** An existing category was chosen instead. */
    MAPPED,
    /** Nothing was added; the change keeps its category. */
    REJECTED
}
