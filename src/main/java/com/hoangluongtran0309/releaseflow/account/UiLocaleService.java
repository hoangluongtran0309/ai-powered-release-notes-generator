package com.hoangluongtran0309.releaseflow.account;

import com.hoangluongtran0309.releaseflow.configuration.UiLanguages;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** The interface language one person chose, which only that person sets. */
@Service
public class UiLocaleService {

    private final AppUserRepository appUserRepository;
    private final UiLanguages languages;

    UiLocaleService(AppUserRepository appUserRepository, UiLanguages languages) {
        this.appUserRepository = appUserRepository;
        this.languages = languages;
    }

    @Transactional(readOnly = true)
    public UiLocaleSettings settings(UUID userId) {
        return settings(find(userId).getUiLocale());
    }

    /**
     * Saves the choice and answers which language it resolved to, or empty when the person
     * asked to follow their browser again.
     */
    @Transactional
    public Optional<Locale> choose(UUID userId, String value) {
        if (value == null || value.isBlank()) {
            find(userId).chooseUiLocale(null);
            return Optional.empty();
        }
        Locale chosen = languages.narrow(value).orElseThrow(() -> new InvalidUiLocaleException(value.strip()));
        find(userId).chooseUiLocale(chosen.toLanguageTag());
        return Optional.of(chosen);
    }

    public UiLocaleSettings settings(String uiLocale) {
        return new UiLocaleSettings(uiLocale, options());
    }

    public List<UiLocaleSettings.Option> options() {
        return languages.supported().stream()
                .map(locale -> new UiLocaleSettings.Option(
                        locale.toLanguageTag(),
                        // Named in its own language, so somebody who cannot read the current
                        // one still recognises theirs.
                        locale.getDisplayLanguage(locale)
                ))
                .toList();
    }

    private AppUser find(UUID userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User " + userId + " does not exist."));
    }
}
