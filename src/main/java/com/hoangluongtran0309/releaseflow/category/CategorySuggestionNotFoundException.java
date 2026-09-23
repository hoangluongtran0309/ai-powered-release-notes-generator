package com.hoangluongtran0309.releaseflow.category;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class CategorySuggestionNotFoundException extends LocalizedException {

    public CategorySuggestionNotFoundException() {
        super("error.category_suggestion_not_found");
    }
}
