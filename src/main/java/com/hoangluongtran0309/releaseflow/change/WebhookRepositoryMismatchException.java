package com.hoangluongtran0309.releaseflow.change;

public class WebhookRepositoryMismatchException extends RuntimeException {

    public WebhookRepositoryMismatchException() {
        super("The delivery does not belong to the repository configured for this webhook.");
    }
}
