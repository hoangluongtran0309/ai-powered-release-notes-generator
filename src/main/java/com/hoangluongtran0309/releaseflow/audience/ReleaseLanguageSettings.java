package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.account.OutputLanguageSettings;

import java.time.Instant;
import java.util.List;

/**
 * The languages release notes are written in, the output language content starts in, and
 * whether a translation provider is configured to translate between them.
 */
public record ReleaseLanguageSettings(
        List<String> targetLanguages,
        String outputLanguage,
        boolean translationEnabled,
        String updatedBy,
        Instant updatedAt,
        List<OutputLanguageSettings.Option> suggestions
) {

    /** Whether some target language needs translation that no provider is configured to do. */
    public boolean translationMissing() {
        return !translationEnabled && targetLanguages.stream()
                .anyMatch(language -> !TargetLanguages.sameLanguage(language, outputLanguage));
    }
}
