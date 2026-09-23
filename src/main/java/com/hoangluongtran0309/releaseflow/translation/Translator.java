package com.hoangluongtran0309.releaseflow.translation;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/** A machine translation provider. It is always called outside a database transaction. */
interface Translator {

    /**
     * @return one translation per text, in the same order
     */
    List<String> translate(List<String> texts, String sourceLanguage, String targetLanguage)
            throws TranslationException;

    static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("A translation provider must not be called inside a database transaction.");
        }
    }
}
