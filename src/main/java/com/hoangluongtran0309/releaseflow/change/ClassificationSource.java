package com.hoangluongtran0309.releaseflow.change;

public enum ClassificationSource {
    RULES,
    AI,
    /** A category the AI proposed and an administrator approved or mapped. */
    SUGGESTION,
    HUMAN
}
