package com.hoangluongtran0309.releaseflow.category;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A category as recorded on a change: a snapshot of its code, name, and group, so that
 * renaming or archiving the category later never rewrites history.
 */
public record CategoryRef(String code, String displayName, CategoryGroup group) {

    public static final int MAX_CODE_LENGTH = 64;
    public static final CategoryRef UNKNOWN = new CategoryRef("UNKNOWN", "Unknown", CategoryGroup.OTHER);

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]*");

    public CategoryRef {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(group, "group must not be null");
    }

    public boolean isUnknown() {
        return UNKNOWN.code().equals(code);
    }

    /**
     * Upper-cases a code and turns dashes and spaces into underscores. The result is
     * empty unless it is a valid code: a letter, then letters, digits, and underscores,
     * at most 64 characters.
     */
    public static Optional<String> normalize(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String code = value.strip().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return code.length() <= MAX_CODE_LENGTH && CODE.matcher(code).matches() ? Optional.of(code) : Optional.empty();
    }
}
