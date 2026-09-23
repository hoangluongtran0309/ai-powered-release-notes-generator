package com.hoangluongtran0309.releaseflow.audience;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** An audience's template for one language; other languages use its main template. */
@Entity
@Table(name = "audience_template_variants")
class AudienceTemplateVariant {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "audience_id", nullable = false, updatable = false)
    private UUID audienceId;

    @Column(nullable = false, length = 16, updatable = false)
    private String language;

    @Column(name = "template_body", nullable = false, columnDefinition = "text")
    private String templateBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AudienceTemplateVariant() {
    }

    AudienceTemplateVariant(UUID organizationId, UUID audienceId, String language, String templateBody, Instant at) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.audienceId = audienceId;
        this.language = language;
        this.templateBody = templateBody;
        this.createdAt = at;
        this.updatedAt = at;
    }

    void update(String templateBody, Instant at) {
        this.templateBody = templateBody;
        this.updatedAt = at;
    }

    UUID getAudienceId() {
        return audienceId;
    }

    String getLanguage() {
        return language;
    }

    String getTemplateBody() {
        return templateBody;
    }
}
