package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class WebhookRepositoryMismatchException extends LocalizedException {

    public WebhookRepositoryMismatchException() {
        super("error.webhook_repository_mismatch");
    }
}
