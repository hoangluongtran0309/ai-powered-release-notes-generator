package com.hoangluongtran0309.releaseflow.gitlab;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class InvalidGitLabBaseUrlException extends LocalizedException {

    public InvalidGitLabBaseUrlException(String messageKey, Object... arguments) {
        super(messageKey, arguments);
    }
}
