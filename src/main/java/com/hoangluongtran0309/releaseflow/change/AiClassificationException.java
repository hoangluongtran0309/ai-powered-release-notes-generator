package com.hoangluongtran0309.releaseflow.change;

/**
 * An AI call that produced no usable classification. The message is fixed text that
 * is safe to store and show: it never contains a key, a response body, or an
 * underlying exception message.
 */
class AiClassificationException extends RuntimeException {

    AiClassificationException(String message) {
        super(message);
    }
}
