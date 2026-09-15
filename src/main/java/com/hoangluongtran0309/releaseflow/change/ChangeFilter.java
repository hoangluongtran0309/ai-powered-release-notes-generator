package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.category.CategoryRef;

/**
 * Optional Change Inbox filters. A null category, status, or context means "all". The
 * category is a code of the catalog, compared with the code each change recorded; the
 * context filter keeps changes whose context was found insufficient.
 */
record ChangeFilter(String category, ReviewStatus status, boolean insufficientContext) {

    static final String INSUFFICIENT_CONTEXT = "insufficient";

    static ChangeFilter parse(String category, String status) {
        return parse(category, status, null);
    }

    static ChangeFilter parse(String category, String status, String context) {
        String parsedCategory = null;
        if (category != null && !category.isBlank()) {
            parsedCategory = CategoryRef.normalize(category).orElseThrow(() -> new InvalidChangeFilterException(
                    "Category must be a category code such as feature or fix."
            ));
        }
        ReviewStatus parsedStatus = null;
        if (status != null && !status.isBlank()) {
            parsedStatus = ReviewStatus.fromValue(status).orElseThrow(() -> new InvalidChangeFilterException(
                    "Status must be one of: needs-review, classified, reviewed."
            ));
        }
        boolean insufficientContext = false;
        if (context != null && !context.isBlank()) {
            if (!INSUFFICIENT_CONTEXT.equals(context.strip())) {
                throw new InvalidChangeFilterException("Context must be insufficient.");
            }
            insufficientContext = true;
        }
        return new ChangeFilter(parsedCategory, parsedStatus, insufficientContext);
    }

    Boolean needsReview() {
        return status == null ? null : status == ReviewStatus.NEEDS_REVIEW;
    }

    // "Classified" means settled by rules or AI without a person; "reviewed" means a person settled it.
    Boolean reviewed() {
        if (status == null || status == ReviewStatus.NEEDS_REVIEW) {
            return null;
        }
        return status == ReviewStatus.REVIEWED;
    }
}
