package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.category.CategoryRef;

import java.util.List;

record ChangeClassification(
        CategoryRef category,
        boolean breaking,
        boolean needsReview,
        List<String> reasons,
        List<ReviewTrigger> triggers
) {

    ChangeClassification {
        reasons = List.copyOf(reasons);
        triggers = List.copyOf(triggers);
    }
}
