package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class SourceSyncInProgressException extends LocalizedException {

    public SourceSyncInProgressException() {
        super("error.source_sync_in_progress");
    }
}
