package com.hoangluongtran0309.releaseflow.project;

import java.util.Optional;

/**
 * A repository and, when an administrator has set one, its decrypted access token.
 * Never serialize or log this value.
 */
public record GitHubRepositoryCredentials(String owner, String repository, String token) {

    public Optional<String> accessToken() {
        return Optional.ofNullable(token);
    }

    @Override
    public String toString() {
        return "GitHubRepositoryCredentials[owner=" + owner + ", repository=" + repository + ", token=***]";
    }
}
