package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** No active automation Rule with that ID belongs to the current Organization. */
public class AutomationRuleNotFoundException extends LocalizedException {

    AutomationRuleNotFoundException() {
        super("error.automation_rule_not_found");
    }
}
