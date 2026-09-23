package com.hoangluongtran0309.releaseflow.change;

public enum ContextStatus {
    SUFFICIENT,
    /** The pull request gave too little evidence for a trustworthy summary; a person reviews it. */
    INSUFFICIENT
}
