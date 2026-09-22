package com.hoangluongtran0309.releaseflow.configuration;

import java.io.Serial;

/**
 * A failure whose explanation a person reads. Its message is a bundle key rather than a
 * sentence, so the same failure can be written in whichever language the reader asked for.
 * The Problem Details {@code code} of a failure never comes from here and never changes.
 */
public abstract class LocalizedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Object[] NO_ARGUMENTS = new Object[0];

    private final Object[] arguments;

    protected LocalizedException(String messageKey, Object... arguments) {
        super(messageKey);
        this.arguments = arguments == null || arguments.length == 0 ? NO_ARGUMENTS : arguments.clone();
    }

    protected LocalizedException(String messageKey, Throwable cause, Object... arguments) {
        super(messageKey, cause);
        this.arguments = arguments == null || arguments.length == 0 ? NO_ARGUMENTS : arguments.clone();
    }

    /** The bundle key of the sentence a person reads. */
    public String messageKey() {
        return getMessage();
    }

    /** What the sentence fills in, such as a limit or a name the reader supplied. */
    public Object[] arguments() {
        return arguments.length == 0 ? NO_ARGUMENTS : arguments.clone();
    }
}
