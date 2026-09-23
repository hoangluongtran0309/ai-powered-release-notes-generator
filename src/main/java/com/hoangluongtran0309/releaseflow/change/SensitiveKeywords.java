package com.hoangluongtran0309.releaseflow.change;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * What stands in for the sensitive-path rules when a source cannot report changed files
 * at all. It reads the title and description, because that is all such a source gives us,
 * which is exactly why it is never the rule for a source that can do better.
 */
final class SensitiveKeywords {

    private static final List<String> KEYWORDS = List.of(
            "breaking change",
            "migration",
            "security",
            "auth",
            "credential",
            "password",
            "encryption"
    );

    private SensitiveKeywords() {
    }

    /** One trigger per keyword the change's own words contain, in the order listed above. */
    static List<ReviewTrigger> matches(String title, String description) {
        String haystack = (Objects.requireNonNullElse(title, "") + " "
                + Objects.requireNonNullElse(description, "")).toLowerCase(Locale.ROOT);
        return KEYWORDS.stream()
                .filter(haystack::contains)
                .map(ReviewTrigger::sensitiveKeyword)
                .toList();
    }
}
