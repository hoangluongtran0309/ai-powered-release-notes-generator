package com.hoangluongtran0309.releaseflow.change;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The size a provider's delivery may not exceed, the same for every provider. */
@Component
class WebhookBody {

    private final int maxBytes;

    WebhookBody(@Value("${releaseflow.webhooks.max-body-bytes}") int maxBytes) {
        if (maxBytes < 1) {
            throw new IllegalStateException("releaseflow.webhooks.max-body-bytes must be at least 1.");
        }
        this.maxBytes = maxBytes;
    }

    void requireWithinLimit(byte[] body) {
        if (body.length > maxBytes) {
            throw new WebhookPayloadTooLargeException(maxBytes);
        }
    }
}
