package com.hoangluongtran0309.releaseflow.release;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "releases")
class Release {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 50)
    private String version;

    @Column(columnDefinition = "text")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReleaseStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "publisher_name", length = 120)
    private String publisherName;

    protected Release() {
    }

    Release(UUID id, UUID organizationId, UUID projectId, String version, String summary, Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.version = version;
        this.summary = summary;
        this.status = ReleaseStatus.DRAFT;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    void edit(String version, String summary, Instant at) {
        this.version = version;
        this.summary = summary;
        this.updatedAt = at;
    }

    void touch(Instant at) {
        this.updatedAt = at;
    }

    void publish(UUID publisher, String name, Instant at) {
        this.status = ReleaseStatus.PUBLISHED;
        this.publishedBy = publisher;
        this.publisherName = name;
        this.publishedAt = at;
        this.updatedAt = at;
    }

    boolean isDraft() {
        return status == ReleaseStatus.DRAFT;
    }

    Instant getPublishedAt() {
        return publishedAt;
    }

    String getPublisherName() {
        return publisherName;
    }

    UUID getId() {
        return id;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getProjectId() {
        return projectId;
    }

    String getVersion() {
        return version;
    }

    String getSummary() {
        return summary;
    }

    ReleaseStatus getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
