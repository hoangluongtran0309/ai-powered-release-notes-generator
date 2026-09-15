package com.hoangluongtran0309.releaseflow.translation;

import java.util.Optional;

/** The configured translation provider, if any. Without one, translations fail at once. */
public final class TranslationProvider {

    private final Translator active;

    TranslationProvider(Translator active) {
        this.active = active;
    }

    Optional<Translator> active() {
        return Optional.ofNullable(active);
    }

    public boolean isEnabled() {
        return active != null;
    }
}
