package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class AiClassificationUnavailableException extends LocalizedException {

    public AiClassificationUnavailableException() {
        super("error.ai_classification_unavailable");
    }
}
