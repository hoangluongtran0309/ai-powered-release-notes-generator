package com.hoangluongtran0309.releaseflow.change;

import java.util.List;

record ChangeClassification(
        ChangeCategory category,
        boolean breaking,
        boolean needsReview,
        List<String> reasons
) {

    ChangeClassification {
        reasons = List.copyOf(reasons);
    }
}
