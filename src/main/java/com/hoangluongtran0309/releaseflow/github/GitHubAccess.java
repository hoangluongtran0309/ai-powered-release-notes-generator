package com.hoangluongtran0309.releaseflow.github;

public enum GitHubAccess {
    /** The token can read the repository's pull requests. */
    GRANTED,
    /** GitHub rejected the token or it cannot see the repository. */
    REJECTED,
    /** GitHub could not be asked: a timeout, network failure, rate limit, or server error. */
    UNAVAILABLE
}
