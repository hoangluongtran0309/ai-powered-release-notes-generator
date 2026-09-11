package com.hoangluongtran0309.releaseflow.change;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum ChangeCategory {
    FEATURE("Feature"),
    FIX("Fix"),
    PERFORMANCE("Performance"),
    DOCUMENTATION("Documentation"),
    MAINTENANCE("Maintenance"),
    UNKNOWN("Unknown");

    private final String label;

    ChangeCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Optional<ChangeCategory> fromValue(String value) {
        return Arrays.stream(values())
                .filter(category -> category.getValue().equals(value))
                .findFirst();
    }
}
