package com.hoangluongtran0309.releaseflow.source;

/** The kinds of system a Project's changes can come from. */
public enum SourceType {
    GITHUB,
    GITLAB,
    /** An issue tracker delivered by webhook: no changed files, no history. */
    LINEAR,
    /** An issue tracker ReleaseFlow asks, rather than one that tells it. */
    JIRA;

    /** The provider's own name, which reads the same in every language. */
    public String displayName() {
        return switch (this) {
            case GITHUB -> "GitHub";
            case GITLAB -> "GitLab";
            case LINEAR -> "Linear";
            case JIRA -> "Jira";
        };
    }

    /** Whether an administrator can ask the provider for what it recorded before connection. */
    public boolean supportsHistoryImport() {
        return this == GITHUB || this == GITLAB;
    }

    /** Whether ReleaseFlow reads this provider on a schedule instead of being delivered to. */
    public boolean isPolled() {
        return this == JIRA;
    }

    /** Whether a delivery of this type has to prove itself, which only a webhook does. */
    public boolean hasWebhook() {
        return !isPolled();
    }
}
