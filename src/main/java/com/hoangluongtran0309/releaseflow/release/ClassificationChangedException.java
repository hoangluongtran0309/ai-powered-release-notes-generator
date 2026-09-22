package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class ClassificationChangedException extends LocalizedException {

    public ClassificationChangedException() {
        super("error.classification_changed");
    }
}
