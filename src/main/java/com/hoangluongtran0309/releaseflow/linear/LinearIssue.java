package com.hoangluongtran0309.releaseflow.linear;

/**
 * What Linear says an issue looks like now. ReleaseFlow reads it back after a delivery
 * so the change carries the issue's current wording rather than the wording it had at
 * the instant it was completed.
 */
public record LinearIssue(String id, String title, String description, String authorName, String url) {
}
