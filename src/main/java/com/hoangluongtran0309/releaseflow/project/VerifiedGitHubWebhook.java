package com.hoangluongtran0309.releaseflow.project;

import java.util.Locale;
import java.util.UUID;

/**
 * A GitHub source whose signing secret verified a delivery. It is the only
 * source of tenant identity for webhook processing.
 */
public record VerifiedGitHubWebhook(
        UUID organizationId,
        UUID projectId,
        UUID sourceId,
        String owner,
        String repository
) {

    public boolean isRepository(String fullName) {
        return fullName != null && fullName.toLowerCase(Locale.ROOT).equals(owner + "/" + repository);
    }
}
