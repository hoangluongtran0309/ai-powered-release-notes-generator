package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class InvalidReleaseScheduleException extends LocalizedException {

    static final String PAST = "error.invalid_release_schedule.past";
    static final String UNREADABLE = "error.invalid_release_schedule.unreadable";

    InvalidReleaseScheduleException(String messageKey) {
        super(messageKey);
    }
}
