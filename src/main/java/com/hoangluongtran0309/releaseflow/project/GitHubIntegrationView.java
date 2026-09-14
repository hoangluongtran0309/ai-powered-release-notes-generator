package com.hoangluongtran0309.releaseflow.project;

import java.time.Instant;
import java.util.UUID;

// The access token itself is write-only; the view only says whether one is set.
public record GitHubIntegrationView(
        UUID id,
        String owner,
        String repository,
        UUID webhookId,
        String webhookPath,
        Instant createdAt,
        Instant lastDeliveryAt,
        boolean accessTokenConfigured,
        Instant accessTokenUpdatedAt
) {
}
