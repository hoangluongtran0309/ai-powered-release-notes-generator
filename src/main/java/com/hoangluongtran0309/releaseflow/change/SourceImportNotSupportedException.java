package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;
import com.hoangluongtran0309.releaseflow.source.SourceType;

/** The provider keeps no history ReleaseFlow could read, so there is nothing to import. */
public class SourceImportNotSupportedException extends LocalizedException {

    SourceImportNotSupportedException(SourceType sourceType) {
        super("error.source_import_not_supported", sourceType.displayName());
    }
}
