package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class ReleaseVersionTakenException extends LocalizedException {

    public ReleaseVersionTakenException() {
        super("error.release_version_taken");
    }
}
