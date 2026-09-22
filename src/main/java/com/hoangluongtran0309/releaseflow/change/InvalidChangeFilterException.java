package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class InvalidChangeFilterException extends LocalizedException {

    public InvalidChangeFilterException(String messageKey, Object... arguments) {
        super(messageKey, arguments);
    }
}
