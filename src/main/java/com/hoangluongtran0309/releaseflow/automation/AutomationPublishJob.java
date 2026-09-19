package com.hoangluongtran0309.releaseflow.automation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * The outbox row a publication leaves behind. Writing it is all automation does
 * inside the transaction that publishes a Release, so no Rule can undo a
 * publication. The worker turns it into Runs once that transaction has committed.
 */
@Entity
@Table(name = "automation_publish_jobs")
class AutomationPublishJob {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "release_id", nullable = false, updatable = false)
    private UUID releaseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "last_error", length = 100)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AutomationPublishJob() {
    }

    AutomationPublishJob(UUID id, UUID organizationId, UUID projectId, UUID releaseId, Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.releaseId = releaseId;
        this.status = Status.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = createdAt;
        this.createdAt = createdAt;
    }

    void claim(Instant now) {
        this.status = Status.RUNNING;
        this.attempts++;
        this.claimedAt = now;
    }

    void succeed(Instant now) {
        this.status = Status.SUCCEEDED;
        this.lastError = null;
        this.completedAt = now;
    }

    void retryLater(String error, Instant nextAttemptAt) {
        this.status = Status.PENDING;
        this.lastError = error;
        this.nextAttemptAt = nextAttemptAt;
    }

    void fail(String error, Instant now) {
        this.status = Status.FAILED;
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

    UUID getReleaseId() {
        return releaseId;
    }

    Status getStatus() {
        return status;
    }

    int getAttempts() {
        return attempts;
    }

    String getLastError() {
        return lastError;
    }

    enum Status {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED
    }
}
