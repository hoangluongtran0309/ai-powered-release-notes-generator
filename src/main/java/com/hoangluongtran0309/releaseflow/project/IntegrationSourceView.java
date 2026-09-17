package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.source.SourceType;
import com.hoangluongtran0309.releaseflow.source.WebhookAuthMode;

import java.time.Instant;
import java.util.UUID;

// The access token itself is write-only; the view only says whether one is set. A
// webhook source has a webhook identity and a path; a polled source has nextPollAt
// instead, and never both.
public record IntegrationSourceView(
        UUID id,
        SourceType type,
        String deliveryMechanism,
        String externalProjectKey,
        String owner,
        String repository,
        String apiBaseUrl,
        String externalWorkspaceKey,
        String credentialIdentity,
        WebhookAuthMode webhookAuthMode,
        UUID webhookId,
        String webhookPath,
        Instant createdAt,
        Instant lastDeliveryAt,
        boolean accessTokenConfigured,
        Instant accessTokenUpdatedAt,
        ConnectionStatus connectionStatus,
        Instant lastSyncAt,
        String lastErrorCode,
        Instant nextPollAt
) {
}
