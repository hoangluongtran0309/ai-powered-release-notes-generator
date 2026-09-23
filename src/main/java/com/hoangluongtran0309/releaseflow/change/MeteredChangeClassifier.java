package com.hoangluongtran0309.releaseflow.change;

/**
 * Counts every request made to the configured provider, exactly once, whatever happens to
 * it. Wrapping the provider rather than instrumenting each one means the three providers
 * cannot drift apart, and every caller — the worker and a person retrying by hand alike —
 * is counted without knowing that it is.
 *
 * <p>Success is recorded only after the answer has been read, so a reply that arrived but
 * could not be understood counts as an error, which is what it was.
 */
final class MeteredChangeClassifier implements AiChangeClassifier, AutoCloseable {

    private final AiChangeClassifier delegate;
    private final ClassificationMetrics metrics;

    MeteredChangeClassifier(AiChangeClassifier delegate, ClassificationMetrics metrics) {
        this.delegate = delegate;
        this.metrics = metrics;
    }

    @Override
    public AiProvider provider() {
        return delegate.provider();
    }

    @Override
    public String model() {
        return delegate.model();
    }

    @Override
    public AiClassification classify(AiClassificationRequest request) {
        try {
            AiClassification classification = delegate.classify(request);
            metrics.recordProviderRequest(delegate.provider(), true);
            return classification;
        } catch (RuntimeException exception) {
            metrics.recordProviderRequest(delegate.provider(), false);
            throw exception;
        }
    }

    @Override
    public void close() throws Exception {
        if (delegate instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }
}
