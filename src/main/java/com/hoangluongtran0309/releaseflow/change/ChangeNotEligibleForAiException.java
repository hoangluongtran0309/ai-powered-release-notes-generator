package com.hoangluongtran0309.releaseflow.change;

public class ChangeNotEligibleForAiException extends RuntimeException {

    public ChangeNotEligibleForAiException() {
        super("Only changes the rules left Unknown can be classified with AI.");
    }
}
