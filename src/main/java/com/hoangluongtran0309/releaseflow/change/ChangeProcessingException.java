package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class ChangeProcessingException extends LocalizedException {

    public ChangeProcessingException() {
        super("error.change_processing");
    }
}
