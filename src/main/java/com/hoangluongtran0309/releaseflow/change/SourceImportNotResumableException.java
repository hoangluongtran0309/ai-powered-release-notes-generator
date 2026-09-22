package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class SourceImportNotResumableException extends LocalizedException {

    public SourceImportNotResumableException() {
        super("error.source_import_not_resumable");
    }
}
