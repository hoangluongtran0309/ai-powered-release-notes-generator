package com.hoangluongtran0309.releaseflow.change;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The sensitive-path patterns an administrator added for one Project. They extend the
 * deployment's baseline and can never remove any of it.
 */
@Entity
@Table(name = "project_sensitive_paths")
class ProjectSensitivePathPolicy {

    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "additional_globs", nullable = false, columnDefinition = "jsonb")
    private List<String> additionalGlobs;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "updater_name", nullable = false, length = 120)
    private String updaterName;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectSensitivePathPolicy() {
    }

    ProjectSensitivePathPolicy(UUID organizationId, UUID projectId) {
        this.organizationId = organizationId;
        this.projectId = projectId;
    }

    void replace(List<String> globs, UUID updater, String name, Instant at) {
        this.additionalGlobs = List.copyOf(globs);
        this.updatedBy = updater;
        this.updaterName = name;
        this.updatedAt = at;
    }

    List<String> getAdditionalGlobs() {
        return additionalGlobs == null ? List.of() : List.copyOf(additionalGlobs);
    }

    String getUpdaterName() {
        return updaterName;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
