package com.hoangluongtran0309.releaseflow.category;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** APPROVED adds the proposed category; MAPPED needs the existing category to use; REJECTED adds nothing. */
public class CategorySuggestionDecisionRequest {

    @NotNull(message = "{validation.categorySuggestion.required}")
    private CategorySuggestionStatus decision;

    private UUID categoryId;

    public CategorySuggestionStatus getDecision() {
        return decision;
    }

    public void setDecision(CategorySuggestionStatus decision) {
        this.decision = decision;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    @AssertTrue(message = "{validation.categorySuggestion.complete}")
    public boolean isDecisionComplete() {
        return decision == null
                || (decision != CategorySuggestionStatus.PENDING_REVIEW
                && (decision != CategorySuggestionStatus.MAPPED || categoryId != null));
    }
}
