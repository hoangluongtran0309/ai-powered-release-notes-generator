package com.hoangluongtran0309.releaseflow.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.IllformedLocaleException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The UI languages this deployment ships translations for, in the configured order. Every
 * language is narrowed to its primary subtag, so a request asking for {@code vi-VN} reads
 * the {@code vi} bundle rather than falling through to the first entry. The list is read
 * once at startup and must name at least one language, because a deployment with no UI
 * language could render no page at all.
 */
@Component
public class UiLanguages {

    private static final Set<String> ISO_LANGUAGES = Set.of(Locale.getISOLanguages());

    private final List<Locale> supported;

    UiLanguages(@Value("${releaseflow.ui-languages}") List<String> configured) {
        LinkedHashSet<Locale> locales = new LinkedHashSet<>();
        for (String entry : configured) {
            if (entry != null && !entry.isBlank()) {
                locales.add(parse(entry));
            }
        }
        if (locales.isEmpty()) {
            throw new IllegalStateException("releaseflow.ui-languages must name at least one language.");
        }
        this.supported = List.copyOf(locales);
    }

    /** The languages a person can choose between, in the order the picker shows them. */
    public List<Locale> supported() {
        return supported;
    }

    /** What a request falls back to when nothing else picks a supported language. */
    public Locale fallback() {
        return supported.getFirst();
    }

    /**
     * The supported language a tag asks for, or empty when this deployment does not ship
     * it. A region is dropped first, so {@code vi-VN} and {@code vi} ask for the same one.
     */
    public Optional<Locale> narrow(String tag) {
        if (tag == null || tag.isBlank()) {
            return Optional.empty();
        }
        try {
            return narrow(new Locale.Builder().setLanguageTag(tag.strip().replace('_', '-')).build());
        } catch (IllformedLocaleException exception) {
            return Optional.empty();
        }
    }

    public Optional<Locale> narrow(Locale locale) {
        if (locale == null) {
            return Optional.empty();
        }
        String language = locale.getLanguage();
        return supported.stream().filter(candidate -> candidate.getLanguage().equals(language)).findFirst();
    }

    private static Locale parse(String entry) {
        final Locale locale;
        try {
            locale = new Locale.Builder().setLanguageTag(entry.strip().replace('_', '-')).build();
        } catch (IllformedLocaleException exception) {
            throw new IllegalStateException(
                    "releaseflow.ui-languages entry is not a language tag: " + entry, exception
            );
        }
        if (!ISO_LANGUAGES.contains(locale.getLanguage())) {
            throw new IllegalStateException("releaseflow.ui-languages entry is not an ISO language: " + entry);
        }
        return Locale.forLanguageTag(locale.getLanguage());
    }
}
