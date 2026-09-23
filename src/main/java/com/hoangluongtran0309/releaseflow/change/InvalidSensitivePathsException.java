package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class InvalidSensitivePathsException extends LocalizedException {

    InvalidSensitivePathsException(String messageKey, Object... arguments) {
        super(messageKey, arguments);
    }
}
