package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.translation.TranslationState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * The note one audience gets in one language for an approved or published release. It
 * is rendered at approval from a snapshot of the audience's template for that language,
 * and follows later summary edits and translations until a person edits it; then it is
 * manual and never rendered again. The database allows writes only while the release is
 * approved, and publication only once every note is ready, so a published note is final.
 */
@Entity
@Table(name = "release_audience_notes")
class AudienceReleaseNote {

    @Id
    private UUID id;

    @Column(name = "release_id", nullable = false, updatable = false)
    private UUID releaseId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "audience_id", nullable = false, updatable = false)
    private UUID audienceId;

    @Column(name = "audience_code", nullable = false, length = 64, updatable = false)
    private String audienceCode;

    @Column(name = "audience_name", nullable = false, length = 120, updatable = false)
    private String audienceName;

    @Column(nullable = false, length = 16, updatable = false)
    private String language;

    @Column(name = "template_body_snapshot", nullable = false, columnDefinition = "text", updatable = false)
    private String templateBodySnapshot;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "auto_rerender", nullable = false)
    private boolean autoRerender;

    @Enumerated(EnumType.STRING)
    @Column(name = "translation_status", nullable = false, length = 10)
    private TranslationState.Status translationStatus;

    @Column(name = "last_edited_by")
    private UUID lastEditedBy;

    @Column(name = "last_editor_name", length = 120)
    private String lastEditorName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AudienceReleaseNote() {
    }

    AudienceReleaseNote(
            UUID id,
            Release release,
            UUID audienceId,
            String audienceCode,
            String audienceName,
            String language,
            String templateBodySnapshot,
            String content,
            TranslationState.Status translationStatus,
            Instant createdAt
    ) {
        this.id = id;
        this.releaseId = release.getId();
        this.organizationId = release.getOrganizationId();
        this.projectId = release.getProjectId();
        this.audienceId = audienceId;
        this.audienceCode = audienceCode;
        this.audienceName = audienceName;
        this.language = language;
        this.templateBodySnapshot = templateBodySnapshot;
        this.content = content;
        this.autoRerender = true;
        this.translationStatus = translationStatus;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /**
     * Replaces the content of a note that still follows its template, with how far its
     * translations have come; a manual note is kept.
     */
    void rerender(String content, TranslationState.Status translationStatus, Instant at) {
        if (!autoRerender || (this.content.equals(content) && this.translationStatus == translationStatus)) {
            return;
        }
        this.content = content;
        this.translationStatus = translationStatus;
        this.updatedAt = at;
    }

    /** A person's text, ready as written. The note never follows its template again. */
    void edit(String content, UUID editor, String editorName, Instant at) {
        this.content = content;
        this.translationStatus = TranslationState.Status.READY;
        this.autoRerender = false;
        this.lastEditedBy = editor;
        this.lastEditorName = editorName;
        this.updatedAt = at;
    }

    AudienceNoteView view() {
        return new AudienceNoteView(
                id,
                audienceId,
                audienceCode,
                audienceName,
                language,
                content,
                autoRerender,
                lastEditorName,
                updatedAt,
                translationStatus
        );
    }

    UUID getId() {
        return id;
    }

    String getAudienceCode() {
        return audienceCode;
    }

    String getAudienceName() {
        return audienceName;
    }

    String getLanguage() {
        return language;
    }

    boolean isAutoRerender() {
        return autoRerender;
    }

    TranslationState.Status getTranslationStatus() {
        return translationStatus;
    }

    String getTemplateBodySnapshot() {
        return templateBodySnapshot;
    }
}
