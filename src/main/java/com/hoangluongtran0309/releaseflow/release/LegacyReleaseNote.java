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
 * The single note of a release published before audiences existed (V8 to V12). No new
 * rows are written; database triggers reject any update or delete. Later releases
 * publish one {@link AudienceReleaseNote} per audience instead.
 */
@Entity
@Immutable
@Table(name = "release_notes")
class LegacyReleaseNote {

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

    protected LegacyReleaseNote() {
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
