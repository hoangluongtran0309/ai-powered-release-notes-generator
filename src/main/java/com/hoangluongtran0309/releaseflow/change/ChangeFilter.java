package com.hoangluongtran0309.releaseflow.change;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Optional Change Inbox filters. A null category or status means "all".
 */
record ChangeFilter(ChangeCategory category, ReviewStatus status) {

    static ChangeFilter parse(String category, String status) {
        ChangeCategory parsedCategory = null;
        if (category != null && !category.isBlank()) {
            parsedCategory = ChangeCategory.fromValue(category).orElseThrow(() -> new InvalidChangeFilterException(
                    "Category must be one of: " + Arrays.stream(ChangeCategory.values())
                            .map(ChangeCategory::getValue)
                            .collect(Collectors.joining(", ")) + "."
            ));
        }
        ReviewStatus parsedStatus = null;
        if (status != null && !status.isBlank()) {
            parsedStatus = ReviewStatus.fromValue(status).orElseThrow(() -> new InvalidChangeFilterException(
                    "Status must be one of: needs-review, classified."
            ));
        }
        return new ChangeFilter(parsedCategory, parsedStatus);
    }

    Boolean needsReview() {
        return status == null ? null : status == ReviewStatus.NEEDS_REVIEW;
    }
}
