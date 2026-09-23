package com.hoangluongtran0309.releaseflow.translation;

/**
 * A translation that did not succeed. The code is a fixed, safe value stored on the job;
 * it never carries the provider's response or the exception text.
 */
class TranslationException extends Exception {

    static final String UNAVAILABLE = "provider_unavailable";
    static final String RATE_LIMITED = "rate_limited";
    static final String REJECTED = "provider_rejected";
    static final String QUOTA_EXCEEDED = "quota_exceeded";
    static final String INVALID_RESPONSE = "invalid_response";

    private final String code;
    private final boolean retryable;

    TranslationException(String code, boolean retryable) {
        super(code);
        this.code = code;
        this.retryable = retryable;
    }

    String code() {
        return code;
    }

    boolean retryable() {
        return retryable;
    }
}
