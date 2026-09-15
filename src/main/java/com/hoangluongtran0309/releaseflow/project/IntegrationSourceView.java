package com.hoangluongtran0309.releaseflow.project;

import java.time.Instant;
import java.util.UUID;

// The access token itself is write-only; the view only says whether one is set. GitHub
// sources deliver changes by signed webhook.
public record IntegrationSourceView(
        UUID id,
        SourceType type,
        String deliveryMechanism,
        String externalProjectKey,
        String owner,
        String repository,
        UUID webhookId,
        String webhookPath,
        Instant createdAt,
        Instant lastDeliveryAt,
        boolean accessTokenConfigured,
        Instant accessTokenUpdatedAt,
        ConnectionStatus connectionStatus,
        Instant lastSyncAt,
        String lastErrorCode
) {
}
