package com.hoangluongtran0309.releaseflow.audience;

/** A change to an Organization's audiences that its current audiences do not allow. */
public class AudienceConflictException extends RuntimeException {

    private final String code;

    private AudienceConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    static AudienceConflictException codeTaken() {
        return new AudienceConflictException("audience_code_taken", "An audience with this code already exists.");
    }

    static AudienceConflictException inUse() {
        return new AudienceConflictException(
                "audience_in_use",
                "This audience already has release notes, so it cannot be deleted."
        );
    }

    static AudienceConflictException last() {
        return new AudienceConflictException(
                "audience_last",
                "An Organization needs at least one audience. Add another before deleting this one."
        );
    }

    static AudienceConflictException limit() {
        return new AudienceConflictException(
                "audience_limit",
                "An Organization can have at most " + AudienceService.MAX_AUDIENCES + " audiences."
        );
    }

    static AudienceConflictException notPreset() {
        return new AudienceConflictException(
                "audience_not_preset",
                "Only a shipped audience can be reset. Your team created this one."
        );
    }

    public String code() {
        return code;
    }
}
