package com.hoangluongtran0309.releaseflow.change;

public class WebhookSignatureInvalidException extends RuntimeException {

    public WebhookSignatureInvalidException() {
        super("The webhook delivery could not be verified.");
    }
}
