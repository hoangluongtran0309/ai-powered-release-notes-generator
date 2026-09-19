package com.hoangluongtran0309.releaseflow.automation;

/** No automation Run with that ID belongs to the current Organization. */
public class AutomationRunNotFoundException extends RuntimeException {

    AutomationRunNotFoundException() {
        super("Automation run not found.");
    }
}
