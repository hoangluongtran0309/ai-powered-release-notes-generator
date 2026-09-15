package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.account.InvalidOutputLanguageException;
import com.hoangluongtran0309.releaseflow.account.OrganizationRegistered;
import com.hoangluongtran0309.releaseflow.account.OutputLanguage;
import com.hoangluongtran0309.releaseflow.account.OutputLanguageService;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An Organization's audiences: the kinds of reader it writes release notes for. Every
 * operation is scoped by Organization, and an Organization always keeps at least one
 * audience, so an approved release always gets a note. Each audience has a template for
 * every target language the output language does not cover, made when the language or
 * the audience is added.
 */
@Service
public class AudienceService {

    static final int MAX_AUDIENCES = 20;

    private static final String CODE_CONSTRAINT = "audience_definitions_code_unique";
    private static final String IN_USE_CONSTRAINT = "release_audience_notes_audience_fk";

    private final AudienceRepository audienceRepository;
    private final AudienceTemplateVariantRepository variantRepository;
    private final TargetLanguages targetLanguages;
    private final OutputLanguageService outputLanguageService;
    private final Clock clock;

    AudienceService(
            AudienceRepository audienceRepository,
            AudienceTemplateVariantRepository variantRepository,
            TargetLanguages targetLanguages,
            OutputLanguageService outputLanguageService,
            Clock clock
    ) {
        this.audienceRepository = audienceRepository;
        this.variantRepository = variantRepository;
        this.targetLanguages = targetLanguages;
        this.outputLanguageService = outputLanguageService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AudienceView> list(UUID organizationId) {
        Map<UUID, Map<String, String>> variants = variants(organizationId);
        return audienceRepository.findAllByOrganizationIdOrderByDisplayNameAscCodeAsc(organizationId).stream()
                .map(audience -> audience.view(variants.getOrDefault(audience.getId(), Map.of())))
                .toList();
    }

    /** The audiences the AI writes narratives for, ordered by code. */
    @Transactional(readOnly = true)
    public List<AudienceBrief> briefs(UUID organizationId) {
        return audienceRepository.findAllByOrganizationIdOrderByCodeAsc(organizationId).stream()
                .map(audience -> new AudienceBrief(audience.getCode(), audience.getCommunicationIntent()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AudienceView get(UUID organizationId, UUID audienceId) {
        return view(find(organizationId, audienceId));
    }

    @Transactional
    public AudienceView create(UUID organizationId, NewAudienceRequest request) {
        List<AudienceDefinition> existing = audienceRepository.lockAllByOrganizationId(organizationId);
        if (existing.size() >= MAX_AUDIENCES) {
            throw AudienceConflictException.limit();
        }
        if (audienceRepository.existsByOrganizationIdAndCode(organizationId, request.getCode())) {
            throw AudienceConflictException.codeTaken();
        }
        AudienceTemplate.validate(request.getTemplateBody());
        AudienceDefinition audience = new AudienceDefinition(
                UUID.randomUUID(),
                organizationId,
                request.getCode(),
                request.getDisplayName(),
                request.intent(),
                request.getTemplateBody(),
                false,
                clock.instant()
        );
        try {
            audienceRepository.saveAndFlush(audience);
        } catch (DataIntegrityViolationException exception) {
            throw violates(exception, CODE_CONSTRAINT) ? AudienceConflictException.codeTaken() : exception;
        }
        addMissingVariants(List.of(audience), targetLanguages.needingVariants(organizationId));
        return view(audience);
    }

    @Transactional
    public AudienceView update(UUID organizationId, UUID audienceId, AudienceRequest request) {
        AudienceDefinition audience = find(organizationId, audienceId);
        AudienceTemplate.validate(request.getTemplateBody());
        audience.update(request.getDisplayName(), request.intent(), request.getTemplateBody(), clock.instant());
        audienceRepository.flush();
        return view(audience);
    }

    /**
     * Writes an audience's template for one of the Organization's target languages.
     *
     * @throws InvalidAudienceTemplateException when the language is not a target language
     *                                          or the template does not render
     */
    @Transactional
    public AudienceView updateTemplate(UUID organizationId, UUID audienceId, String languageTag, String templateBody) {
        AudienceDefinition audience = find(organizationId, audienceId);
        String language = targetLanguage(organizationId, languageTag);
        AudienceTemplate.validate(templateBody);
        Instant now = clock.instant();
        variantRepository.findByAudienceIdAndOrganizationIdAndLanguage(audienceId, organizationId, language)
                .ifPresentOrElse(
                        variant -> variant.update(templateBody, now),
                        () -> variantRepository.save(new AudienceTemplateVariant(organizationId, audienceId, language,
                                templateBody, now))
                );
        variantRepository.flush();
        return view(audience);
    }

    /** Deletes an audience that no release note uses, unless it is the last one. */
    @Transactional
    public void delete(UUID organizationId, UUID audienceId) {
        List<AudienceDefinition> audiences = audienceRepository.lockAllByOrganizationId(organizationId);
        AudienceDefinition audience = audiences.stream()
                .filter(candidate -> candidate.getId().equals(audienceId))
                .findFirst()
                .orElseThrow(AudienceNotFoundException::new);
        if (audiences.size() <= 1) {
            throw AudienceConflictException.last();
        }
        try {
            audienceRepository.delete(audience);
            audienceRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw violates(exception, IN_USE_CONSTRAINT) ? AudienceConflictException.inUse() : exception;
        }
    }

    /**
     * Restores a shipped audience's name, intent, and template in the current output
     * language, and each of its language templates to the shipped version in that language.
     */
    @Transactional
    public AudienceView resetToPreset(UUID organizationId, UUID audienceId) {
        AudienceDefinition audience = find(organizationId, audienceId);
        if (!audience.isPreset()) {
            throw AudienceConflictException.notPreset();
        }
        OutputLanguage language = outputLanguageService.outputLanguage(organizationId);
        AudiencePresets.Preset preset = AudiencePresets.find(audience.getCode(), language.tag())
                .orElseThrow(AudienceConflictException::notPreset);
        Instant now = clock.instant();
        audience.update(preset.displayName(), preset.communicationIntent(), preset.templateBody(), now);
        variantRepository.findAllByOrganizationIdOrderByLanguageAsc(organizationId).stream()
                .filter(variant -> variant.getAudienceId().equals(audienceId))
                .forEach(variant -> variant.update(variantBody(audience, variant.getLanguage()), now));
        audienceRepository.flush();
        return view(audience);
    }

    /** Renders a template with sample values; it is validated exactly as on save. */
    public AudiencePreview preview(String templateBody) {
        String markdown = AudienceTemplate.sample(templateBody);
        return new AudiencePreview(markdown, MarkdownHtml.render(markdown));
    }

    /** Gives every audience a template for each of these languages it does not have yet. */
    @Transactional
    void addMissingVariants(UUID organizationId, List<String> languages) {
        addMissingVariants(audienceRepository.findAllByOrganizationIdOrderByCodeAsc(organizationId), languages);
    }

    // Runs inside the registration transaction; a failure rolls the registration back.
    @EventListener
    void seedPresets(OrganizationRegistered event) {
        Instant now = clock.instant();
        List<AudienceDefinition> presets = AudiencePresets.forLanguage(event.outputLanguage().tag()).stream()
                .map(preset -> new AudienceDefinition(
                        UUID.randomUUID(),
                        event.organizationId(),
                        preset.code(),
                        preset.displayName(),
                        preset.communicationIntent(),
                        preset.templateBody(),
                        true,
                        now
                ))
                .toList();
        audienceRepository.saveAll(presets);
    }

    private void addMissingVariants(List<AudienceDefinition> audiences, List<String> languages) {
        if (audiences.isEmpty() || languages.isEmpty()) {
            return;
        }
        UUID organizationId = audiences.getFirst().getOrganizationId();
        Set<String> existing = variantRepository.findAllByOrganizationIdOrderByLanguageAsc(organizationId).stream()
                .map(variant -> variant.getAudienceId() + "/" + variant.getLanguage())
                .collect(Collectors.toSet());
        Instant now = clock.instant();
        for (AudienceDefinition audience : audiences) {
            for (String language : languages) {
                if (!existing.contains(audience.getId() + "/" + language)) {
                    variantRepository.save(new AudienceTemplateVariant(organizationId, audience.getId(), language,
                            variantBody(audience, language), now));
                }
            }
        }
        variantRepository.flush();
    }

    // A shipped audience starts from its shipped template in that language; any other from its main template.
    private static String variantBody(AudienceDefinition audience, String language) {
        if (!audience.isPreset()) {
            return audience.getTemplateBody();
        }
        return AudiencePresets.find(audience.getCode(), language)
                .map(AudiencePresets.Preset::templateBody)
                .orElse(audience.getTemplateBody());
    }

    private String targetLanguage(UUID organizationId, String languageTag) {
        final String language;
        try {
            language = OutputLanguage.parse(languageTag).tag();
        } catch (InvalidOutputLanguageException exception) {
            throw InvalidAudienceTemplateException.languageNotTargeted(languageTag);
        }
        if (!targetLanguages.of(organizationId).contains(language)) {
            throw InvalidAudienceTemplateException.languageNotTargeted(language);
        }
        return language;
    }

    private AudienceView view(AudienceDefinition audience) {
        return audience.view(variants(audience.getOrganizationId()).getOrDefault(audience.getId(), Map.of()));
    }

    // Audience ID -> language -> template.
    private Map<UUID, Map<String, String>> variants(UUID organizationId) {
        Map<UUID, Map<String, String>> variants = new HashMap<>();
        for (AudienceTemplateVariant variant : variantRepository.findAllByOrganizationIdOrderByLanguageAsc(organizationId)) {
            variants.computeIfAbsent(variant.getAudienceId(), ignored -> new HashMap<>())
                    .put(variant.getLanguage(), variant.getTemplateBody());
        }
        return variants;
    }

    private AudienceDefinition find(UUID organizationId, UUID audienceId) {
        return audienceRepository.findByIdAndOrganizationId(audienceId, organizationId)
                .orElseThrow(AudienceNotFoundException::new);
    }

    private static boolean violates(DataIntegrityViolationException exception, String constraint) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }
}
