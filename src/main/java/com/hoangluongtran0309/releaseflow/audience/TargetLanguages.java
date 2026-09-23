package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.account.OutputLanguageService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** The languages an Organization's release notes are written in; its output language until chosen. */
@Component
class TargetLanguages {

    private final OrganizationTranslationSettingsRepository settingsRepository;
    private final OutputLanguageService outputLanguageService;

    TargetLanguages(
            OrganizationTranslationSettingsRepository settingsRepository,
            OutputLanguageService outputLanguageService
    ) {
        this.settingsRepository = settingsRepository;
        this.outputLanguageService = outputLanguageService;
    }

    @Transactional(readOnly = true)
    List<String> of(UUID organizationId) {
        return settingsRepository.findById(organizationId)
                .map(OrganizationTranslationSettings::getTargetLanguages)
                .orElseGet(() -> List.of(outputLanguageService.outputLanguage(organizationId).tag()));
    }

    /** The target languages that need their own template: those the output language does not already cover. */
    @Transactional(readOnly = true)
    List<String> needingVariants(UUID organizationId) {
        String output = outputLanguageService.outputLanguage(organizationId).tag();
        return of(organizationId).stream().filter(language -> !sameLanguage(language, output)).toList();
    }

    static boolean sameLanguage(String first, String second) {
        return Locale.forLanguageTag(first).getLanguage().equals(Locale.forLanguageTag(second).getLanguage());
    }
}
