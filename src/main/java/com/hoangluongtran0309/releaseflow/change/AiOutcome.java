package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.OutputLanguage;

import java.util.Objects;

/**
 * What one AI attempt produced: a validated classification, or a safe failure
 * message. Never both.
 */
record AiOutcome(
        AiProvider provider,
        String model,
        AiClassification classification,
        OutputLanguage language,
        String failure
) {

    static final String DID_NOT_FINISH = "AI classification did not finish.";

    AiOutcome {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(model, "model must not be null");
        if ((classification == null) == (failure == null)) {
            throw new IllegalArgumentException("An AI outcome is either a classification or a failure.");
        }
        if (classification != null) {
            Objects.requireNonNull(language, "language must not be null");
        }
    }

    static AiOutcome succeeded(AiChangeClassifier classifier, AiClassification classification, OutputLanguage language) {
        return new AiOutcome(classifier.provider(), classifier.model(), classification, language, null);
    }

    static AiOutcome failed(AiChangeClassifier classifier, String failure) {
        return new AiOutcome(classifier.provider(), classifier.model(), null, null, failure);
    }

    boolean succeeded() {
        return classification != null;
    }
}
