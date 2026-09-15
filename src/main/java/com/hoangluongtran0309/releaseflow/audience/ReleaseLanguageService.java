package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.account.InvalidOutputLanguageException;
import com.hoangluongtran0309.releaseflow.account.OutputLanguage;
import com.hoangluongtran0309.releaseflow.account.OutputLanguageService;
import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.translation.TranslationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The languages an Organization's release notes are written in: one note per audience
 * and language at approval. Until an administrator chooses, only the output language.
 */
@Service
public class ReleaseLanguageService {

    static final int MAX_LANGUAGES = 5;

    private final OrganizationTranslationSettingsRepository settingsRepository;
    private final TargetLanguages targetLanguages;
    private final AudienceService audienceService;
    private final OutputLanguageService outputLanguageService;
    private final TranslationService translationService;
    private final Clock clock;

    ReleaseLanguageService(
            OrganizationTranslationSettingsRepository settingsRepository,
            TargetLanguages targetLanguages,
            AudienceService audienceService,
            OutputLanguageService outputLanguageService,
            TranslationService translationService,
            Clock clock
    ) {
        this.settingsRepository = settingsRepository;
        this.targetLanguages = targetLanguages;
        this.audienceService = audienceService;
        this.outputLanguageService = outputLanguageService;
        this.translationService = translationService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<String> targetLanguages(UUID organizationId) {
        return targetLanguages.of(organizationId);
    }

    @Transactional(readOnly = true)
    public ReleaseLanguageSettings settings(UUID organizationId) {
        OrganizationTranslationSettings settings = settingsRepository.findById(organizationId).orElse(null);
        return new ReleaseLanguageSettings(
                targetLanguages.of(organizationId),
                outputLanguageService.outputLanguage(organizationId).tag(),
                translationService.isEnabled(),
                settings == null ? null : settings.getUpdaterName(),
                settings == null ? null : settings.getUpdatedAt(),
                outputLanguageService.suggestions()
        );
    }

    /**
     * Replaces the target languages and gives every audience a template for each new one.
     *
     * @throws InvalidReleaseLanguagesException for an invalid tag, or fewer than one or
     *                                          more than {@value #MAX_LANGUAGES} languages
     */
    @Transactional
    public ReleaseLanguageSettings replace(ReleaseFlowPrincipal administrator, ReleaseLanguagesRequest request) {
        UUID organizationId = administrator.organizationId();
        List<String> languages = normalize(request.getTargetLanguages());
        OrganizationTranslationSettings settings = settingsRepository.findById(organizationId)
                .orElseGet(() -> new OrganizationTranslationSettings(organizationId));
        settings.replace(languages, administrator.userId(), administrator.displayName(), clock.instant());
        settingsRepository.saveAndFlush(settings);
        audienceService.addMissingVariants(organizationId, targetLanguages.needingVariants(organizationId));
        return settings(organizationId);
    }

    // Canonical tags without repeats, in the order given.
    static List<String> normalize(List<String> values) {
        Set<String> languages = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                languages.add(OutputLanguage.parse(value).tag());
            } catch (InvalidOutputLanguageException exception) {
                throw new InvalidReleaseLanguagesException(exception.getMessage());
            }
        }
        if (languages.isEmpty() || languages.size() > MAX_LANGUAGES) {
            throw new InvalidReleaseLanguagesException(
                    "Choose between one and " + MAX_LANGUAGES + " release note languages."
            );
        }
        return List.copyOf(languages);
    }
}
