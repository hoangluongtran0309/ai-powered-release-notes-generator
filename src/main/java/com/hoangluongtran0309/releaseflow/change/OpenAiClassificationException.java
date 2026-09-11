package com.hoangluongtran0309.releaseflow.change;

/**
 * An OpenAI call that produced no usable classification. The message is fixed text
 * that is safe to store and show: it never contains the API key or a response body.
 */
class OpenAiClassificationException extends RuntimeException {

    OpenAiClassificationException(String message) {
        super(message);
    }
}
