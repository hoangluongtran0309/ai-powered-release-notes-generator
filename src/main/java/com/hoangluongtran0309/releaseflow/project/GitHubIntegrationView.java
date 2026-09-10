package com.hoangluongtran0309.releaseflow.project;

import java.time.Instant;
import java.util.UUID;

public record GitHubIntegrationView(
        UUID id,
        String owner,
        String repository,
        UUID webhookId,
        String webhookPath,
        Instant createdAt
) {
}
