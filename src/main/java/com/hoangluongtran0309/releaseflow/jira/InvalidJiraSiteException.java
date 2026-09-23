package com.hoangluongtran0309.releaseflow.jira;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class InvalidJiraSiteException extends LocalizedException {

    public InvalidJiraSiteException(String messageKey, Object... arguments) {
        super(messageKey, arguments);
    }
}
