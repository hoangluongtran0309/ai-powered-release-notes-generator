package com.hoangluongtran0309.releaseflow.project;

import java.util.Optional;
import java.util.UUID;

/** What the two webhook verifiers share once a delivery's source has been resolved. */
final class WebhookSources {

    private WebhookSources() {
    }

    static Optional<UUID> parseWebhookId(String webhookId) {
        try {
            return Optional.of(UUID.fromString(webhookId));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }
    }

    static VerifiedWebhook verified(IntegrationSource source) {
        return new VerifiedWebhook(
                source.getOrganizationId(),
                source.getProjectId(),
                source.getId(),
                source.getSourceType(),
                source.getExternalProjectKey()
        );
    }
}
