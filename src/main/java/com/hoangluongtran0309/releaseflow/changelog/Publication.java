package com.hoangluongtran0309.releaseflow.changelog;

import java.util.UUID;

/**
 * What became of one attempt to make a release note public, and the address readers
 * will find it at.
 */
public record Publication(Outcome outcome, String url) {

    public enum Outcome {

        /** The note is public: either this call published it, or it already was. */
        PUBLISHED,

        /**
         * This release, audience, and language are already public with different
         * words. Published material is never overwritten, so the delivery fails and a
         * person decides what to do.
         */
        CONFLICT
    }

    /** One note offered to the public changelog. */
    public record Request(
            UUID actionRunId,
            UUID organizationId,
            UUID projectId,
            UUID releaseId,
            String releaseVersion,
            String audienceName,
            String language,
            String content
    ) {
    }
}
