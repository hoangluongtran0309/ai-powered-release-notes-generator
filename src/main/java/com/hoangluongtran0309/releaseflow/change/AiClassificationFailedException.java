package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/**
 * Thrown after a failed AI attempt has already been recorded on the change. The recorded
 * failure is a fixed English sentence kept as it was stored; the wording around it is the
 * reader's own.
 */
public class AiClassificationFailedException extends LocalizedException {

    public AiClassificationFailedException(String recordedFailure) {
        super("error.ai_classification_failed", recordedFailure);
    }
}
