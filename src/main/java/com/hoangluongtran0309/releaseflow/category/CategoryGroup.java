package com.hoangluongtran0309.releaseflow.category;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The fixed kinds of change a category belongs to. Release notes are sectioned by
 * group, and the fixed rules name a group when their preferred category is missing.
 * Breaking is a separate flag, not a group.
 */
public enum CategoryGroup {
    FEATURE("Feature"),
    FIX("Fix"),
    PERFORMANCE("Performance"),
    DOCUMENTATION("Documentation"),
    MAINTENANCE("Maintenance"),
    OTHER("Other");

    private final String label;

    CategoryGroup(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * The bundle key of this CategoryGroup's name, so a page says it in the reader's language.
     * {@link #getLabel()} stays the English wording that recorded evidence keeps.
     */
    public String getLabelKey() {
        return "ui.enum.categoryGroup." + name();
    }

    public static Optional<CategoryGroup> fromValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(group -> group.name().equals(normalized)).findFirst();
    }
}
