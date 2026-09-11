package com.hoangluongtran0309.releaseflow.change;

/**
 * Thrown after a failed AI attempt has already been recorded on the change.
 */
public class AiClassificationFailedException extends RuntimeException {

    public AiClassificationFailedException(String message) {
        super(message);
    }
}
