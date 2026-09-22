package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class ChangeNotEligibleForAiException extends LocalizedException {

    public ChangeNotEligibleForAiException() {
        super("error.change_not_eligible_for_ai");
    }
}
