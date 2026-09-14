package com.hoangluongtran0309.releaseflow.change;

import java.util.Optional;

/**
 * The configured AI provider, if any. With none, changes are classified by the
 * rules alone.
 */
final class AiClassifiers implements AutoCloseable {

    private final AiChangeClassifier active;

    AiClassifiers(AiChangeClassifier active) {
        this.active = active;
    }

    Optional<AiChangeClassifier> active() {
        return Optional.ofNullable(active);
    }

    boolean isEnabled() {
        return active != null;
    }

    @Override
    public void close() throws Exception {
        if (active instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }
}
