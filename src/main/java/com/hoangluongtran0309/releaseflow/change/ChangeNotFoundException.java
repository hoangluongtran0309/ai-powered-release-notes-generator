package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class ChangeNotFoundException extends LocalizedException {

    public ChangeNotFoundException() {
        super("error.change_not_found");
    }
}
