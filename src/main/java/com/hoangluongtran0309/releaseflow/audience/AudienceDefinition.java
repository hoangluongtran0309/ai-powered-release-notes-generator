package com.hoangluongtran0309.releaseflow.audience;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One kind of reader an Organization writes release notes for. The code never changes
 * after creation, because stored narratives are keyed by it.
 */
@Entity
@Table(name = "audience_definitions")
class AudienceDefinition {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 64, updatable = false)
    private String code;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "communication_intent", nullable = false, length = 1000)
    private String communicationIntent;

    @Column(name = "template_body", nullable = false, columnDefinition = "text")
    private String templateBody;

    @Column(nullable = false, updatable = false)
    private boolean preset;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AudienceDefinition() {
    }

    AudienceDefinition(
            UUID id,
            UUID organizationId,
            String code,
            String displayName,
            String communicationIntent,
            String templateBody,
            boolean preset,
            Instant createdAt
    ) {
        this.id = id;
        this.organizationId = organizationId;
        this.code = code;
        this.displayName = displayName;
        this.communicationIntent = communicationIntent;
        this.templateBody = templateBody;
        this.preset = preset;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    void update(String displayName, String communicationIntent, String templateBody, Instant at) {
        this.displayName = displayName;
        this.communicationIntent = communicationIntent;
        this.templateBody = templateBody;
        this.updatedAt = at;
    }

    AudienceView view() {
        return new AudienceView(id, code, displayName, communicationIntent, templateBody, preset, createdAt, updatedAt);
    }

    UUID getId() {
        return id;
    }

    String getCode() {
        return code;
    }

    String getCommunicationIntent() {
        return communicationIntent;
    }

    boolean isPreset() {
        return preset;
    }
}
