package com.hoangluongtran0309.releaseflow.change;

public class ChangeProcessingException extends RuntimeException {

    public ChangeProcessingException() {
        super("This change is still being processed. Review it once its changed files are checked.");
    }
}
