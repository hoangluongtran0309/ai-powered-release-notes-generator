package com.hoangluongtran0309.releaseflow.changelog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One published release note, made public. Everything a reader sees is snapshotted
 * here, so renaming the Organization, the Project, or the audience never rewrites what
 * was published, and the entry outlives the Rule that made it. Nothing may change it:
 * the database refuses an update or a delete outright.
 */
@Entity
@Table(name = "public_changelog_entries")
class PublicChangelogEntry {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "release_id", nullable = false, updatable = false)
    private UUID releaseId;

    @Column(name = "action_run_id", nullable = false, updatable = false)
    private UUID actionRunId;

    @Column(name = "organization_slug_snapshot", nullable = false, updatable = false, length = 63)
    private String organizationSlugSnapshot;

    @Column(name = "organization_name_snapshot", nullable = false, updatable = false, length = 120)
    private String organizationNameSnapshot;

    @Column(name = "project_name_snapshot", nullable = false, updatable = false, length = 120)
    private String projectNameSnapshot;

    @Column(name = "release_version_snapshot", nullable = false, updatable = false, length = 50)
    private String releaseVersionSnapshot;

    @Column(name = "audience_name_snapshot", nullable = false, updatable = false, length = 120)
    private String audienceNameSnapshot;

    @Column(nullable = false, updatable = false, length = 16)
    private String language;

    @Column(name = "content_snapshot", nullable = false, updatable = false, columnDefinition = "text")
    private String contentSnapshot;

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;

    protected PublicChangelogEntry() {
    }

    PublicChangelogEntry(
            UUID id,
            UUID organizationId,
            UUID projectId,
            UUID releaseId,
            UUID actionRunId,
            String organizationSlug,
            String organizationName,
            String projectName,
            String releaseVersion,
            String audienceName,
            String language,
            String content,
            Instant publishedAt
    ) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.releaseId = releaseId;
        this.actionRunId = actionRunId;
        this.organizationSlugSnapshot = organizationSlug;
        this.organizationNameSnapshot = organizationName;
        this.projectNameSnapshot = projectName;
        this.releaseVersionSnapshot = releaseVersion;
        this.audienceNameSnapshot = audienceName;
        this.language = language;
        this.contentSnapshot = content;
        this.publishedAt = publishedAt;
    }

    /** Whether a second delivery is saying exactly what this entry already says. */
    boolean says(String releaseVersion, String content) {
        return releaseVersionSnapshot.equals(releaseVersion) && contentSnapshot.equals(content);
    }

    UUID getId() {
        return id;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    String getOrganizationSlugSnapshot() {
        return organizationSlugSnapshot;
    }

    String getProjectNameSnapshot() {
        return projectNameSnapshot;
    }

    String getReleaseVersionSnapshot() {
        return releaseVersionSnapshot;
    }

    String getAudienceNameSnapshot() {
        return audienceNameSnapshot;
    }

    String getLanguage() {
        return language;
    }

    String getContentSnapshot() {
        return contentSnapshot;
    }

    Instant getPublishedAt() {
        return publishedAt;
    }
}
