package com.hoangluongtran0309.releaseflow.category;

/** A change to the catalog or to a category suggestion that its current state does not allow. */
public class CategoryConflictException extends RuntimeException {

    private final String code;

    private CategoryConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    static CategoryConflictException codeTaken() {
        return new CategoryConflictException("category_code_taken", "A category with this code already exists.");
    }

    static CategoryConflictException system() {
        return new CategoryConflictException(
                "category_system",
                "A system category keeps its group and cannot be archived. You can rename it."
        );
    }

    static CategoryConflictException decided() {
        return new CategoryConflictException(
                "category_suggestion_decided",
                "This category suggestion has already been decided."
        );
    }

    public String code() {
        return code;
    }
}
