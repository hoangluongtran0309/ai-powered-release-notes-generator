package com.hoangluongtran0309.releaseflow.release;

public class TranslationsNotReadyException extends RuntimeException {

    public TranslationsNotReadyException() {
        super("Some release notes are still being translated or could not be translated. "
                + "Retry the translations or edit those notes, then publish.");
    }
}
