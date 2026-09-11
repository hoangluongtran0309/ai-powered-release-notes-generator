package com.hoangluongtran0309.releaseflow.release;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * The published snapshot of a release. It is written once; database triggers reject
 * any later update or delete.
 */
@Entity
@Immutable
@Table(name = "release_notes")
class ReleaseNote {

    @Id
    @Column(name = "release_id")
    private UUID releaseId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 50)
    private String version;

    @Column(columnDefinition = "text")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String sections;

    @Column(nullable = false, columnDefinition = "text")
    private String markdown;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    protected ReleaseNote() {
    }

    ReleaseNote(
            UUID releaseId,
            UUID organizationId,
            UUID projectId,
            String version,
            String summary,
            String sections,
            String markdown,
            Instant publishedAt
    ) {
        this.releaseId = releaseId;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.version = version;
        this.summary = summary;
        this.sections = sections;
        this.markdown = markdown;
        this.publishedAt = publishedAt;
    }

    String getVersion() {
        return version;
    }

    String getSummary() {
        return summary;
    }

    String getSections() {
        return sections;
    }

    String getMarkdown() {
        return markdown;
    }

    Instant getPublishedAt() {
        return publishedAt;
    }
}
