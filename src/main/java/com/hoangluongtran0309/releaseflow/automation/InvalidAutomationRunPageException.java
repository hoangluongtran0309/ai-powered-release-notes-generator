package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** A run history page nobody can ask for. */
public class InvalidAutomationRunPageException extends LocalizedException {

    InvalidAutomationRunPageException(String messageKey, Object... arguments) {
        super(messageKey, arguments);
    }
}
