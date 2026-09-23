package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class SourceTokenMissingException extends LocalizedException {

    public SourceTokenMissingException() {
        super("error.source_token_missing");
    }
}
