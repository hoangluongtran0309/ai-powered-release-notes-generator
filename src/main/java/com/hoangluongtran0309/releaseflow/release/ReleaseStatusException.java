package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** The release is not in the status the requested operation needs. */
public class ReleaseStatusException extends LocalizedException {

    public ReleaseStatusException(String messageKey, Object... arguments) {
        super(messageKey, arguments);
    }
}
