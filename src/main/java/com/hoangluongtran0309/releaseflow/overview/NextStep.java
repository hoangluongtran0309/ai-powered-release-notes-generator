package com.hoangluongtran0309.releaseflow.overview;

import com.hoangluongtran0309.releaseflow.change.ChangeCounts;
import com.hoangluongtran0309.releaseflow.release.ReleaseCounts;

/**
 * The one thing the workspace overview offers to do next. The order is fixed and the
 * first match wins, so somebody who opens ReleaseFlow is never shown two starting points
 * at once.
 */
public enum NextStep {

    /** Nothing has been created yet, so there is no Project to connect anything to. */
    CREATE_PROJECT,
    /** A Project exists but nothing delivers changes to it. */
    CONNECT_SOURCE,
    /** A source is connected and nothing has arrived through it yet. */
    AWAIT_CHANGES,
    /** Processed changes belong to no release. */
    COLLECT_CHANGES,
    /** A release is in review and its changes each need a decision. */
    FINISH_REVIEW,
    /** A release is approved and its notes are waiting to be published. */
    PUBLISH_RELEASE,
    /** A draft is open and needs its changes chosen before review. */
    CONTINUE_DRAFT,
    /** Everything that could be done has been done. */
    NOTHING_WAITING;

    /**
     * The first unfinished thing, so the overview offers one starting point rather than a
     * list of everything that could be done. A workspace with nothing in it is waiting for
     * its first change even when a draft is already open: there is nothing to put in it.
     */
    static NextStep of(boolean projectExists, boolean sourceConnected, ChangeCounts changes, ReleaseCounts releases) {
        if (!projectExists) {
            return CREATE_PROJECT;
        }
        if (!sourceConnected) {
            return CONNECT_SOURCE;
        }
        if (changes.total() == 0) {
            return AWAIT_CHANGES;
        }
        if (releases.unassignedChanges() > 0) {
            return COLLECT_CHANGES;
        }
        if (releases.inReview() > 0) {
            return FINISH_REVIEW;
        }
        if (releases.approved() > 0) {
            return PUBLISH_RELEASE;
        }
        if (releases.drafts() > 0) {
            return CONTINUE_DRAFT;
        }
        return NOTHING_WAITING;
    }

    public String getLabelKey() {
        return "ui.overview.next." + name();
    }

    public String getBodyKey() {
        return "ui.overview.next." + name() + ".body";
    }

    public String getActionKey() {
        return "ui.overview.next." + name() + ".action";
    }
}
