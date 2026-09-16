package com.hoangluongtran0309.releaseflow.source;

/** How a source's deliveries prove they came from the provider ReleaseFlow connected. */
public enum WebhookAuthMode {
    /** GitHub: hex HMAC-SHA256 of the raw body in {@code X-Hub-Signature-256}. */
    GITHUB_HMAC,
    /** GitLab: Standard Webhooks headers, a signed delivery ID, timestamp, and body. */
    GITLAB_SIGNING_TOKEN,
    /** GitLab: the secret repeated verbatim in {@code X-Gitlab-Token}. */
    GITLAB_SECRET_TOKEN
}
