package com.hoangluongtran0309.releaseflow.audience;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * An audience with its main template and its templates for other languages, keyed by
 * language tag.
 */
public record AudienceView(
        UUID id,
        String code,
        String displayName,
        String communicationIntent,
        String templateBody,
        boolean preset,
        Instant createdAt,
        Instant updatedAt,
        Map<String, String> templateVariants
) {

    public AudienceView {
        templateVariants = templateVariants == null
                ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(templateVariants));
    }

    /** The template for one language: its variant if there is one, otherwise the main template. */
    public String templateFor(String language) {
        return templateVariants.getOrDefault(language, templateBody);
    }
}
