package com.hoangluongtran0309.releaseflow.category;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class CategoryNotFoundException extends LocalizedException {

    public CategoryNotFoundException() {
        super("error.category_not_found");
    }
}
