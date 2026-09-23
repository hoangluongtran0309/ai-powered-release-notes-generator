package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class SourceNotFoundException extends LocalizedException {

    public SourceNotFoundException() {
        super("error.source_not_found");
    }
}
