package com.hoangluongtran0309.releaseflow.category;

public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException() {
        super("Category was not found.");
    }
}
