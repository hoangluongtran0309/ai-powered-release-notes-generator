package com.hoangluongtran0309.releaseflow.release;

/**
 * A reviewer's decision on one change of a release in review: confirm the classification
 * as shown, or correct it. Rejecting a change removes it from the release instead.
 */
public enum ReviewAction {
    APPROVE,
    EDIT
}
