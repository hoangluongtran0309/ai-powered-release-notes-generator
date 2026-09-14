package com.hoangluongtran0309.releaseflow.change;

public class ChangeNotEligibleForAiException extends RuntimeException {

    public ChangeNotEligibleForAiException() {
        super("Only a change whose AI classification failed, or an Unknown change recorded before automatic AI, can be sent to AI.");
    }
}
