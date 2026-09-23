package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class MalformedWebhookPayloadException extends LocalizedException {

    public MalformedWebhookPayloadException(String messageKey, Object... arguments) {
        super(messageKey, arguments);
    }
}
