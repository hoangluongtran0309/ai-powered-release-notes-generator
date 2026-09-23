package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class ReleasePublishedException extends LocalizedException {

    public ReleasePublishedException() {
        super("error.release_published");
    }
}
