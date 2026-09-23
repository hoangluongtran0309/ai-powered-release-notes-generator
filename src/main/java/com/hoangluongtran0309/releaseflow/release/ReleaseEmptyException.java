package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class ReleaseEmptyException extends LocalizedException {

    public ReleaseEmptyException() {
        super("error.release_empty");
    }
}
