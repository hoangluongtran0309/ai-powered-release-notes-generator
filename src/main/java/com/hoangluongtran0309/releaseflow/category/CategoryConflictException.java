package com.hoangluongtran0309.releaseflow.category;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** A change to the catalog or to a category suggestion that its current state does not allow. */
public class CategoryConflictException extends LocalizedException {

    private final String code;

    private CategoryConflictException(String code, Object... arguments) {
        super("error." + code, arguments);
        this.code = code;
    }

    static CategoryConflictException codeTaken() {
        return new CategoryConflictException("category_code_taken");
    }

    static CategoryConflictException system() {
        return new CategoryConflictException("category_system");
    }

    static CategoryConflictException decided() {
        return new CategoryConflictException("category_suggestion_decided");
    }

    public String code() {
        return code;
    }
}
