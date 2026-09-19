package com.hoangluongtran0309.releaseflow.automation;

/** No active automation Rule with that ID belongs to the current Organization. */
public class AutomationRuleNotFoundException extends RuntimeException {

    AutomationRuleNotFoundException() {
        super("Automation rule not found.");
    }
}
