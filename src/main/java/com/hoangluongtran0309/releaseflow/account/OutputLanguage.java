package com.hoangluongtran0309.releaseflow.account;

import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Set;

/**
 * A canonical BCP 47 language tag for generated content, such as {@code en} or
 * {@code vi-VN}. Any tag whose primary language is an ISO 639 code is accepted; the
 * configured list only suggests common choices.
 */
public record OutputLanguage(String tag) {

    public static final OutputLanguage DEFAULT = new OutputLanguage("en");

    static final int MAX_LENGTH = 16;
    private static final Set<String> ISO_LANGUAGES = Set.of(Locale.getISOLanguages());

    public static OutputLanguage parse(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidOutputLanguageException("error.output_language_invalid.required");
        }
        final Locale locale;
        try {
            locale = new Locale.Builder().setLanguageTag(value.strip().replace('_', '-')).build();
        } catch (IllformedLocaleException exception) {
            throw invalid(value);
        }
        if (!ISO_LANGUAGES.contains(locale.getLanguage())) {
            throw invalid(value);
        }
        String canonical = locale.toLanguageTag();
        if (canonical.length() > MAX_LENGTH) {
            throw new InvalidOutputLanguageException(
                    "error.output_language_invalid.tooLong", MAX_LENGTH
            );
        }
        return new OutputLanguage(canonical);
    }

    /** The language's English name, for example "Vietnamese" or "Portuguese (Brazil)". */
    public String displayName() {
        return Locale.forLanguageTag(tag).getDisplayName(Locale.ENGLISH);
    }

    private static InvalidOutputLanguageException invalid(String value) {
        return new InvalidOutputLanguageException(
                "error.output_language_invalid.tag", value.strip()
        );
    }
}
