package com.hoangluongtran0309.releaseflow.change;

public class SourceSyncInProgressException extends RuntimeException {

    public SourceSyncInProgressException() {
        super("An import of this source is already under way.");
    }
}
