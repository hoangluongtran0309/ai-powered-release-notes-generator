package com.hoangluongtran0309.releaseflow.configuration;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Writes a sentence in the language of the request being answered. A key with no
 * translation comes back as the key itself rather than as an English fallback, so a gap
 * is visible on the page and in tests instead of hiding behind one working language.
 */
@Component
public class UiMessages {

    private final MessageSource messageSource;

    UiMessages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String get(String key, Object... arguments) {
        return get(LocaleContextHolder.getLocale(), key, arguments);
    }

    public String get(Locale locale, String key, Object... arguments) {
        return messageSource.getMessage(key, arguments, locale);
    }

    /** The reader's own wording of a failure. */
    public String of(LocalizedException exception) {
        return get(exception.messageKey(), exception.arguments());
    }
}
