package com.hoangluongtran0309.releaseflow.release;

/**
 * What the workspace overview says about one Project's releases, and how many of its
 * processed changes are still waiting to join one.
 */
public record ReleaseCounts(
        long total,
        long drafts,
        long inReview,
        long approved,
        long published,
        long unassignedChanges
) {

    public static final ReleaseCounts NONE = new ReleaseCounts(0, 0, 0, 0, 0, 0);
}
