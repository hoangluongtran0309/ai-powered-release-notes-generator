package com.hoangluongtran0309.releaseflow.account;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** A language this deployment does not ship the interface in. */
public class InvalidUiLocaleException extends LocalizedException {

    InvalidUiLocaleException(String value) {
        super("error.ui_locale_invalid", value);
    }
}
