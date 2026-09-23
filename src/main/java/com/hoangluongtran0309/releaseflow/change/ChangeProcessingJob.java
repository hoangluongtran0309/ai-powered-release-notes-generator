package com.hoangluongtran0309.releaseflow.change;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable work to collect one change's files and classify it, outside the webhook
 * request. Each claim runs in its own short transaction.
 */
@Entity
@Table(name = "change_processing_jobs")
class ChangeProcessingJob {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "change_id", nullable = false)
    private UUID changeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_error", length = 100)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChangeProcessingJob() {
    }

    ChangeProcessingJob(UUID id, Change change, Instant createdAt) {
        this.id = id;
        this.organizationId = change.getOrganizationId();
        this.projectId = change.getProjectId();
        this.changeId = change.getId();
        this.status = Status.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = createdAt;
        this.createdAt = createdAt;
    }

    void claim(Instant now) {
        this.status = Status.ENRICHING;
        this.attempts++;
        this.claimedAt = now;
    }

    // True only for the worker holding the current claim; a claim that went stale and was
    // taken over by another worker no longer belongs to it.
    boolean isClaimedAt(Instant claim) {
        return (status == Status.ENRICHING || status == Status.CLASSIFYING) && claim.equals(claimedAt);
    }

    // From here on the single AI call may be in flight; a stale job must not repeat it.
    void startClassifying() {
        if (status != Status.ENRICHING) {
            throw new IllegalStateException("Job " + id + " is not collecting changed files.");
        }
        this.status = Status.CLASSIFYING;
    }

    boolean isFallbackRequired() {
        return status == Status.FALLBACK_REQUIRED;
    }

    void retry(String error, Instant nextAttemptAt) {
        this.status = Status.PENDING;
        this.lastError = error;
        this.nextAttemptAt = nextAttemptAt;
    }

    void complete(String error, Instant now) {
        this.status = Status.COMPLETED;
        this.lastError = error;
        this.completedAt = now;
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

    UUID getChangeId() {
        return changeId;
    }

    Status getStatus() {
        return status;
    }

    int getAttempts() {
        return attempts;
    }

    Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    Instant getClaimedAt() {
        return claimedAt;
    }

    String getLastError() {
        return lastError;
    }

    enum Status {
        PENDING,
        ENRICHING,
        CLASSIFYING,
        FALLBACK_REQUIRED,
        COMPLETED
    }
}
