package com.hoangluongtran0309.releaseflow.change;

public class SourceImportNotResumableException extends RuntimeException {

    public SourceImportNotResumableException() {
        super("Only an import that stopped at its limit or failed can be resumed.");
    }
}
