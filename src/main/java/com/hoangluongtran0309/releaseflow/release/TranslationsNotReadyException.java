package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class TranslationsNotReadyException extends LocalizedException {

    public TranslationsNotReadyException() {
        super("error.translations_not_ready");
    }
}
