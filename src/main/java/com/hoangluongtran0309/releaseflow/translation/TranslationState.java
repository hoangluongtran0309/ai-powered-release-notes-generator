package com.hoangluongtran0309.releaseflow.translation;

import java.util.Map;

/**
 * Where one change's content stands in one language. Only a ready state carries texts;
 * they are keyed as they were given, and texts that were blank come back unchanged.
 */
public record TranslationState(Status status, Map<String, String> texts) {

    public enum Status {
        READY,
        PENDING,
        FAILED
    }

    public TranslationState {
        texts = texts == null ? Map.of() : Map.copyOf(texts);
    }

    static TranslationState ready(Map<String, String> texts) {
        return new TranslationState(Status.READY, texts);
    }

    static TranslationState pending() {
        return new TranslationState(Status.PENDING, Map.of());
    }

    static TranslationState failed() {
        return new TranslationState(Status.FAILED, Map.of());
    }

    public boolean ready() {
        return status == Status.READY;
    }
}
