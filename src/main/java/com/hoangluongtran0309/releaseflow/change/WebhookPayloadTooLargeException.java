package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** The delivery was refused before it was read, so nothing about it is trusted. */
public class WebhookPayloadTooLargeException extends LocalizedException {

    public WebhookPayloadTooLargeException(int maxBytes) {
        super("error.webhook_payload_too_large", maxBytes);
    }
}
