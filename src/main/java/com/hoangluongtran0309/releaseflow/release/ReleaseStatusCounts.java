package com.hoangluongtran0309.releaseflow.release;

/** How many of a Project's releases stand at each stage. */
record ReleaseStatusCounts(long total, long drafts, long inReview, long approved, long published) {
}
