package com.hoangluongtran0309.releaseflow.account;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class InvalidOrganizationSlugException extends LocalizedException {

    public InvalidOrganizationSlugException(String messageKey, Object... arguments) {
        super(messageKey, arguments);
    }
}
