package com.hoangluongtran0309.releaseflow.release;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** The latest review decision on one change of a release in review. */
@Entity
@Table(name = "release_change_reviews")
@IdClass(ReleaseChange.Key.class)
class ReleaseChangeReview {

    @Id
    @Column(name = "release_id")
    private UUID releaseId;

    @Id
    @Column(name = "change_id")
    private UUID changeId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReviewAction action;

    @Column(name = "reviewer_id", nullable = false)
    private UUID reviewerId;

    @Column(name = "reviewer_name", nullable = false, length = 120)
    private String reviewerName;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected ReleaseChangeReview() {
    }

    ReleaseChangeReview(UUID releaseId, UUID changeId, UUID organizationId, UUID projectId) {
        this.releaseId = releaseId;
        this.changeId = changeId;
        this.organizationId = organizationId;
        this.projectId = projectId;
    }

    void record(ReviewAction action, UUID reviewerId, String reviewerName, String note, Instant at) {
        this.action = action;
        this.reviewerId = reviewerId;
        this.reviewerName = reviewerName;
        this.note = note;
        this.decidedAt = at;
    }

    ReleaseDecisionView view() {
        return new ReleaseDecisionView(changeId, action, reviewerName, note, decidedAt);
    }

    UUID getChangeId() {
        return changeId;
    }
}
