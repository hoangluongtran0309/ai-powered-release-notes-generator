package com.hoangluongtran0309.releaseflow.source;

import java.util.Optional;

/**
 * What a provider call needs about a source: how to address its project and, when an
 * administrator has set one, its decrypted access token. {@code apiBaseUrl} is set for
 * a source whose instance the Organization chose, {@code repositoryOwner} and
 * {@code repositoryName} only for GitHub, and {@code externalWorkspaceKey} only for a
 * provider whose projects live inside a workspace. Never serialize or log this value.
 */
public record SourceCredentials(
        SourceType sourceType,
        String externalProjectKey,
        String externalWorkspaceKey,
        String apiBaseUrl,
        String repositoryOwner,
        String repositoryName,
        String token
) {

    public Optional<String> accessToken() {
        return Optional.ofNullable(token);
    }

    @Override
    public String toString() {
        return "SourceCredentials[sourceType=%s, externalProjectKey=%s, apiBaseUrl=%s, token=***]"
                .formatted(sourceType, externalProjectKey, apiBaseUrl);
    }
}
