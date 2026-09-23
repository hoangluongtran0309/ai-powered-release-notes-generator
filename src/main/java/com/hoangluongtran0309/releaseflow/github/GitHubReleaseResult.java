package com.hoangluongtran0309.releaseflow.github;

/**
 * What happened while reading or publishing a GitHub Release. {@code UNAVAILABLE} is
 * kept apart from {@code REJECTED} because a write that could not be confirmed may
 * still have been carried out.
 */
public record GitHubReleaseResult(Status status, GitHubRelease release) {

    public enum Status {
        /** The release was read or created; {@code release} holds it. */
        FOUND,
        /** The tag has no release. */
        ABSENT,
        /** GitHub refused to create it because the tag already has one. */
        EXISTS,
        /** GitHub refused the call for good: the token cannot do this. */
        REJECTED,
        /** GitHub could not be reached or answered an error worth trying again. */
        UNAVAILABLE,
        /** GitHub answered something that is not a release. */
        INVALID_RESPONSE
    }
}
