package com.hoangluongtran0309.releaseflow.change;

/** A delivery larger than a provider ever sends is refused before anything is looked up. */
public class WebhookPayloadTooLargeException extends RuntimeException {

    public WebhookPayloadTooLargeException(int maxBytes) {
        super("A webhook delivery must not exceed " + maxBytes + " bytes.");
    }
}
