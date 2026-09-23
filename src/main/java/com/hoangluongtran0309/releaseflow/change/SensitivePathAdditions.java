package com.hoangluongtran0309.releaseflow.change;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The patterns an administrator adds for one Project, checked before they are saved. */
final class SensitivePathAdditions {

    static final int MAX_PATTERNS = 100;
    static final int MAX_LENGTH = 256;

    private SensitivePathAdditions() {
    }

    /**
     * Trims each pattern and drops blank lines and repeats, keeping the order.
     *
     * @throws InvalidSensitivePathsException when there are too many patterns, or one is
     *                                        too long or not a valid glob
     */
    static List<String> normalize(List<String> patterns) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String pattern : patterns) {
            String trimmed = pattern == null ? "" : pattern.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > MAX_LENGTH) {
                throw new InvalidSensitivePathsException(
                        "error.invalid_sensitive_paths.patternTooLong", MAX_LENGTH, abbreviate(trimmed));
            }
            normalized.add(trimmed);
        }
        if (normalized.size() > MAX_PATTERNS) {
            throw new InvalidSensitivePathsException("error.invalid_sensitive_paths.tooMany", MAX_PATTERNS);
        }
        for (String pattern : normalized) {
            try {
                SensitivePaths.compile(List.of(pattern));
            } catch (IllegalArgumentException exception) {
                throw new InvalidSensitivePathsException("error.invalid_sensitive_paths.notGlob", pattern);
            }
        }
        return List.copyOf(normalized);
    }

    private static String abbreviate(String pattern) {
        return pattern.substring(0, 40) + "…";
    }
}
