package com.hoangluongtran0309.releaseflow.change;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A later change that looks like an earlier one. It is evidence for review only: a
 * decision records what a person concluded and never merges or removes a change.
 */
@Entity
@Table(name = "duplicate_candidates")
class DuplicateCandidate {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "change_id", nullable = false, updatable = false)
    private UUID changeId;

    @Column(name = "duplicate_of_id", nullable = false, updatable = false)
    private UUID duplicateOfId;

    @Column(nullable = false, updatable = false)
    private double similarity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private DuplicateEvidence evidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DuplicateCandidateStatus status;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decider_name", length = 120)
    private String deciderName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected DuplicateCandidate() {
    }

    DuplicateCandidate(Change change, Change earlier, double similarity, DuplicateEvidence evidence, Instant at) {
        this.id = UUID.randomUUID();
        this.organizationId = change.getOrganizationId();
        this.projectId = change.getProjectId();
        this.changeId = change.getId();
        this.duplicateOfId = earlier.getId();
        this.similarity = similarity;
        this.evidence = evidence;
        this.status = DuplicateCandidateStatus.OPEN;
        this.createdAt = at;
    }

    void decide(DuplicateCandidateStatus decision, UUID decider, String name, Instant at) {
        if (status != DuplicateCandidateStatus.OPEN) {
            throw new DuplicateCandidateDecidedException();
        }
        if (decision == DuplicateCandidateStatus.OPEN) {
            throw new IllegalArgumentException("A decision must not be open.");
        }
        this.status = decision;
        this.decidedBy = decider;
        this.deciderName = name;
        this.decidedAt = at;
    }

    UUID getId() {
        return id;
    }

    UUID getChangeId() {
        return changeId;
    }

    UUID getDuplicateOfId() {
        return duplicateOfId;
    }

    double getSimilarity() {
        return similarity;
    }

    DuplicateEvidence getEvidence() {
        return evidence;
    }

    DuplicateCandidateStatus getStatus() {
        return status;
    }

    String getDeciderName() {
        return deciderName;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getDecidedAt() {
        return decidedAt;
    }
}
