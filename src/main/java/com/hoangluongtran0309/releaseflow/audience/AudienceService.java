package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.account.OrganizationRegistered;
import com.hoangluongtran0309.releaseflow.account.OutputLanguage;
import com.hoangluongtran0309.releaseflow.account.OutputLanguageService;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An Organization's audiences: the kinds of reader it writes release notes for. Every
 * operation is scoped by Organization, and an Organization always keeps at least one
 * audience, so an approved release always gets a note.
 */
@Service
public class AudienceService {

    static final int MAX_AUDIENCES = 20;

    private static final String CODE_CONSTRAINT = "audience_definitions_code_unique";
    private static final String IN_USE_CONSTRAINT = "release_audience_notes_audience_fk";

    private final AudienceRepository audienceRepository;
    private final OutputLanguageService outputLanguageService;
    private final Clock clock;

    AudienceService(AudienceRepository audienceRepository, OutputLanguageService outputLanguageService, Clock clock) {
        this.audienceRepository = audienceRepository;
        this.outputLanguageService = outputLanguageService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AudienceView> list(UUID organizationId) {
        return audienceRepository.findAllByOrganizationIdOrderByDisplayNameAscCodeAsc(organizationId).stream()
                .map(AudienceDefinition::view)
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
        return find(organizationId, audienceId).view();
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
        return audience.view();
    }

    @Transactional
    public AudienceView update(UUID organizationId, UUID audienceId, AudienceRequest request) {
        AudienceDefinition audience = find(organizationId, audienceId);
        AudienceTemplate.validate(request.getTemplateBody());
        audience.update(request.getDisplayName(), request.intent(), request.getTemplateBody(), clock.instant());
        audienceRepository.flush();
        return audience.view();
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

    /** Restores a shipped audience's name, intent, and template in the current output language. */
    @Transactional
    public AudienceView resetToPreset(UUID organizationId, UUID audienceId) {
        AudienceDefinition audience = find(organizationId, audienceId);
        if (!audience.isPreset()) {
            throw AudienceConflictException.notPreset();
        }
        OutputLanguage language = outputLanguageService.outputLanguage(organizationId);
        AudiencePresets.Preset preset = AudiencePresets.find(audience.getCode(), language.tag())
                .orElseThrow(AudienceConflictException::notPreset);
        audience.update(preset.displayName(), preset.communicationIntent(), preset.templateBody(), clock.instant());
        audienceRepository.flush();
        return audience.view();
    }

    /** Renders a template with sample values; it is validated exactly as on save. */
    public AudiencePreview preview(String templateBody) {
        String markdown = AudienceTemplate.sample(templateBody);
        return new AudiencePreview(markdown, MarkdownHtml.render(markdown));
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
