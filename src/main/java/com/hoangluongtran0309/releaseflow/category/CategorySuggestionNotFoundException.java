package com.hoangluongtran0309.releaseflow.category;

public class CategorySuggestionNotFoundException extends RuntimeException {

    public CategorySuggestionNotFoundException() {
        super("Category suggestion was not found.");
    }
}
