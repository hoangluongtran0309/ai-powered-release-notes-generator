package com.hoangluongtran0309.releaseflow.release;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A release moves from DRAFT through IN_REVIEW and APPROVED to PUBLISHED. It can return
 * to draft until it is published, and nothing about it changes once it is published.
 */
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

    @Column(name = "planned_release_at")
    private Instant plannedReleaseAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approver_name", length = 120)
    private String approverName;

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
        requireDraft();
        this.version = version;
        this.summary = summary;
        this.updatedAt = at;
    }

    void touch(Instant at) {
        this.updatedAt = at;
    }

    /** Sets or, with {@code null}, clears the planned release time, which must lie ahead. */
    void schedule(Instant plannedReleaseAt, Instant now) {
        requireUnpublished();
        if (plannedReleaseAt != null && !plannedReleaseAt.isAfter(now)) {
            throw new InvalidReleaseScheduleException(InvalidReleaseScheduleException.PAST);
        }
        this.plannedReleaseAt = plannedReleaseAt;
        this.updatedAt = now;
    }

    void requestReview(Instant at) {
        requireStatus(ReleaseStatus.DRAFT, "Only a draft release can be sent to review.");
        this.status = ReleaseStatus.IN_REVIEW;
        this.updatedAt = at;
    }

    void approve(UUID approver, String name, Instant at) {
        requireInReview();
        this.status = ReleaseStatus.APPROVED;
        this.approvedBy = approver;
        this.approverName = name;
        this.approvedAt = at;
        this.updatedAt = at;
    }

    void returnToDraft(Instant at) {
        requireUnpublished();
        if (status != ReleaseStatus.IN_REVIEW && status != ReleaseStatus.APPROVED) {
            throw new ReleaseStatusException("Only a release that is in review or approved can return to draft.");
        }
        this.status = ReleaseStatus.DRAFT;
        this.approvedBy = null;
        this.approverName = null;
        this.approvedAt = null;
        this.updatedAt = at;
    }

    void publish(UUID publisher, String name, Instant at) {
        requireStatus(ReleaseStatus.APPROVED, "Approve this release before publishing it.");
        this.status = ReleaseStatus.PUBLISHED;
        this.publishedBy = publisher;
        this.publisherName = name;
        this.publishedAt = at;
        this.updatedAt = at;
    }

    void requireUnpublished() {
        if (status == ReleaseStatus.PUBLISHED) {
            throw new ReleasePublishedException();
        }
    }

    void requireDraft() {
        requireStatus(ReleaseStatus.DRAFT, "Return this release to draft to change its details or its changes.");
    }

    void requireInReview() {
        requireStatus(ReleaseStatus.IN_REVIEW, "Request review of this release first. Decisions and approval need a release in review.");
    }

    // A draft chooses its changes; rejecting one during review removes it.
    void requireChangesRemovable() {
        requireUnpublished();
        if (status != ReleaseStatus.DRAFT && status != ReleaseStatus.IN_REVIEW) {
            throw new ReleaseStatusException("The changes of an approved release are fixed. Return it to draft first.");
        }
    }

    private void requireStatus(ReleaseStatus expected, String message) {
        requireUnpublished();
        if (status != expected) {
            throw new ReleaseStatusException(message);
        }
    }

    boolean isPublished() {
        return status == ReleaseStatus.PUBLISHED;
    }

    Instant getPublishedAt() {
        return publishedAt;
    }

    String getPublisherName() {
        return publisherName;
    }

    Instant getPlannedReleaseAt() {
        return plannedReleaseAt;
    }

    Instant getApprovedAt() {
        return approvedAt;
    }

    String getApproverName() {
        return approverName;
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
