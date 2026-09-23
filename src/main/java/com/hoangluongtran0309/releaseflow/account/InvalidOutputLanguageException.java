package com.hoangluongtran0309.releaseflow.account;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class InvalidOutputLanguageException extends LocalizedException {

    public InvalidOutputLanguageException(String messageKey, Object... arguments) {
        super(messageKey, arguments);
    }
}
