package com.hoangluongtran0309.releaseflow.change;

import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * One AI provider. Each call makes exactly one request, never inside a database
 * transaction, and turns every failure into an {@link AiClassificationException}
 * with a safe, fixed message.
 */
interface AiChangeClassifier {

    AiProvider provider();

    String model();

    AiClassification classify(AiClassificationRequest request);

    static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("The AI provider must not be called inside a database transaction.");
        }
    }
}
