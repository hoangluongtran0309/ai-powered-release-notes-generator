package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class ReleaseNotFoundException extends LocalizedException {

    public ReleaseNotFoundException() {
        super("error.release_not_found");
    }
}
