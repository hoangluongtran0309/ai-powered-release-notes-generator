package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.source.SourceType;

/** The provider keeps no history ReleaseFlow could read, so there is nothing to import. */
public class SourceImportNotSupportedException extends RuntimeException {

    SourceImportNotSupportedException(SourceType sourceType) {
        super("A " + sourceType.name().charAt(0) + sourceType.name().substring(1).toLowerCase()
                + " source has no history to import.");
    }
}
