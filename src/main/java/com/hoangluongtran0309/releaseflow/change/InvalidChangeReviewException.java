package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class InvalidChangeReviewException extends LocalizedException {

    public InvalidChangeReviewException() {
        super("error.invalid_change_review");
    }
}
