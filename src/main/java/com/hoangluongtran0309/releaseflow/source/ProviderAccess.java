package com.hoangluongtran0309.releaseflow.source;

public enum ProviderAccess {
    /** The token can read the project's pull or merge requests. */
    GRANTED,
    /** The provider rejected the token or it cannot see the project. */
    REJECTED,
    /** The provider could not be asked: a timeout, network failure, rate limit, or server error. */
    UNAVAILABLE
}
