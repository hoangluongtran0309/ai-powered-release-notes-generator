package com.hoangluongtran0309.releaseflow.audience;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The languages an Organization's release notes are written in, as an administrator chose them. */
@Entity
@Table(name = "organization_translation_settings")
class OrganizationTranslationSettings {

    @Id
    @Column(name = "organization_id")
    private UUID organizationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_languages", nullable = false, columnDefinition = "jsonb")
    private List<String> targetLanguages;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "updater_name", nullable = false, length = 120)
    private String updaterName;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrganizationTranslationSettings() {
    }

    OrganizationTranslationSettings(UUID organizationId) {
        this.organizationId = organizationId;
    }

    void replace(List<String> languages, UUID updater, String name, Instant at) {
        this.targetLanguages = List.copyOf(languages);
        this.updatedBy = updater;
        this.updaterName = name;
        this.updatedAt = at;
    }

    List<String> getTargetLanguages() {
        return List.copyOf(targetLanguages);
    }

    String getUpdaterName() {
        return updaterName;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
