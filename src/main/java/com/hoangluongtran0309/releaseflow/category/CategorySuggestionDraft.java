package com.hoangluongtran0309.releaseflow.category;

import java.util.Objects;

/** A category the AI proposed because none in the catalog fit. It is never active on its own. */
public record CategorySuggestionDraft(String code, String displayName, CategoryGroup group, String rationale) {

    public static final int RATIONALE_LIMIT = 1000;

    public CategorySuggestionDraft {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(group, "group must not be null");
        rationale = rationale == null ? "" : rationale;
    }
}
