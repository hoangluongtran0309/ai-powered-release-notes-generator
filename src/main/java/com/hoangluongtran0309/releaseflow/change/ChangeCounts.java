package com.hoangluongtran0309.releaseflow.change;

/** What the workspace overview says about one Project's changes. */
public record ChangeCounts(long total, long needsReview, long breaking) {

    public static final ChangeCounts NONE = new ChangeCounts(0, 0, 0);
}
