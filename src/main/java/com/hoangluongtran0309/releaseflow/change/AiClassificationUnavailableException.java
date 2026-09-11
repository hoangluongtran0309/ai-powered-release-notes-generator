package com.hoangluongtran0309.releaseflow.change;

public class AiClassificationUnavailableException extends RuntimeException {

    public AiClassificationUnavailableException() {
        super("AI classification is not configured.");
    }
}
