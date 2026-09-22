package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class InvalidReleaseLanguagesException extends LocalizedException {

    InvalidReleaseLanguagesException(String messageKey, Object... arguments) {
        super(messageKey, arguments);
    }
}
