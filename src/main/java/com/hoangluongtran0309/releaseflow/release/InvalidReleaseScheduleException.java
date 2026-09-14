package com.hoangluongtran0309.releaseflow.release;

public class InvalidReleaseScheduleException extends RuntimeException {

    static final String PAST = "The planned release time must be in the future.";
    static final String UNREADABLE =
            "Enter the planned release time as an ISO-8601 date and time, for example 2026-10-01T09:00:00Z.";

    InvalidReleaseScheduleException(String message) {
        super(message);
    }
}
