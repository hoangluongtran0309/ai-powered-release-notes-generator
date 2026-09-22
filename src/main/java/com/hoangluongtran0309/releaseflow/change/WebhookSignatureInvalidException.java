package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class WebhookSignatureInvalidException extends LocalizedException {

    public WebhookSignatureInvalidException() {
        super("error.webhook_signature_invalid");
    }
}
