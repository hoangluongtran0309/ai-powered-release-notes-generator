package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.source.SourceType;

import java.util.Locale;
import java.util.UUID;

/**
 * A source whose own secret verified a delivery. It is the only source of tenant
 * identity for webhook processing.
 */
public record VerifiedWebhook(
        UUID organizationId,
        UUID projectId,
        UUID sourceId,
        SourceType sourceType,
        String externalProjectKey
) {

    /** Whether the delivery names the project this source is connected to. */
    public boolean isProject(String key) {
        return key != null && key.toLowerCase(Locale.ROOT).equals(externalProjectKey);
    }
}
