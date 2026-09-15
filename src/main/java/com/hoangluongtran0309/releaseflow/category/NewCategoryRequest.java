package com.hoangluongtran0309.releaseflow.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/** A new category. Its code is upper-cased, with dashes and spaces turned into underscores, and fixed once created. */
public class NewCategoryRequest extends CategoryRequest {

    @NotBlank(message = "Code is required.")
    @Size(max = CategoryRef.MAX_CODE_LENGTH, message = "Code must not exceed 64 characters.")
    @Pattern(regexp = "[A-Z][A-Z0-9_]*", message = "Use letters, digits, and underscores, starting with a letter.")
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code == null ? null : code.strip().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
