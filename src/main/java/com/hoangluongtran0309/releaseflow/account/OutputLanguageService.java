package com.hoangluongtran0309.releaseflow.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * The language each Organization's generated content is written in. Changes apply to
 * later classifications only.
 */
@Service
public class OutputLanguageService {

    private final OrganizationRepository organizationRepository;
    private final List<OutputLanguage> suggestions;

    OutputLanguageService(
            OrganizationRepository organizationRepository,
            @Value("${releaseflow.output-languages}") List<String> suggestions
    ) {
        this.organizationRepository = organizationRepository;
        // Validated at startup so a typo in the list is caught before anyone picks it.
        this.suggestions = List.copyOf(new LinkedHashSet<>(suggestions.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(OutputLanguage::parse)
                .toList()));
    }

    @Transactional(readOnly = true)
    public OutputLanguage outputLanguage(UUID organizationId) {
        return find(organizationId).getOutputLanguage();
    }

    @Transactional(readOnly = true)
    public OutputLanguageSettings settings(UUID organizationId) {
        return settings(find(organizationId).getOutputLanguage());
    }

    @Transactional
    public OutputLanguageSettings change(UUID organizationId, String value) {
        OutputLanguage outputLanguage = OutputLanguage.parse(value);
        find(organizationId).changeOutputLanguage(outputLanguage);
        return settings(outputLanguage);
    }

    public List<OutputLanguageSettings.Option> suggestions() {
        return suggestions.stream()
                .map(language -> new OutputLanguageSettings.Option(language.tag(), language.displayName()))
                .toList();
    }

    private OutputLanguageSettings settings(OutputLanguage outputLanguage) {
        return new OutputLanguageSettings(outputLanguage.tag(), outputLanguage.displayName(), suggestions());
    }

    private Organization find(UUID organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalStateException("Organization " + organizationId + " does not exist."));
    }
}
