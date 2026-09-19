package com.hoangluongtran0309.releaseflow.github;

/** A GitHub Release as ReleaseFlow reads it back: where it is, and what it says. */
public record GitHubRelease(String htmlUrl, String body) {
}
