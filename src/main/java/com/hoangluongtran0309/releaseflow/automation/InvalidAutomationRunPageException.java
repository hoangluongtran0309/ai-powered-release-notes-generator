package com.hoangluongtran0309.releaseflow.automation;

/** A run history page nobody can ask for. */
public class InvalidAutomationRunPageException extends RuntimeException {

    InvalidAutomationRunPageException(String message) {
        super(message);
    }
}
