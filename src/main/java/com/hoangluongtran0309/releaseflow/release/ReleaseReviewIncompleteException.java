package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class ReleaseReviewIncompleteException extends LocalizedException {

    public ReleaseReviewIncompleteException() {
        super("error.release_review_incomplete");
    }
}
