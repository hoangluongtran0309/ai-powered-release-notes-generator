package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** A change to an Organization's audiences that its current audiences do not allow. */
public class AudienceConflictException extends LocalizedException {

    private final String code;

    private AudienceConflictException(String code, Object... arguments) {
        super("error." + code, arguments);
        this.code = code;
    }

    static AudienceConflictException codeTaken() {
        return new AudienceConflictException("audience_code_taken");
    }

    static AudienceConflictException inUse() {
        return new AudienceConflictException("audience_in_use");
    }

    static AudienceConflictException last() {
        return new AudienceConflictException("audience_last");
    }

    static AudienceConflictException limit() {
        return new AudienceConflictException("audience_limit");
    }

    static AudienceConflictException notPreset() {
        return new AudienceConflictException("audience_not_preset");
    }

    public String code() {
        return code;
    }
}
