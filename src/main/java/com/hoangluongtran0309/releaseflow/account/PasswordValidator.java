package com.hoangluongtran0309.releaseflow.account;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        // A missing value is reported once, by @NotBlank.
        if (password == null || password.isBlank()) {
            return true;
        }
        return password.length() >= 12
                && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
