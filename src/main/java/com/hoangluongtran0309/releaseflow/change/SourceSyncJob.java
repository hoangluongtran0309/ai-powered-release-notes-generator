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
 * Durable work that reads a source's history within a time window. Its cursor and
 * counts are saved after every page, so a stopped or limited import resumes where it
 * left off. Each claim runs in its own short transaction.
 */
@Entity
@Table(name = "source_sync_jobs")
class SourceSyncJob {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "source_id", nullable = false, updatable = false)
    private UUID sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 30, updatable = false)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "window_start", nullable = false, updatable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false, updatable = false)
    private Instant windowEnd;

    @Column(name = "provider_cursor", nullable = false, length = 500)
    private String providerCursor;

    @Column(name = "scanned_count", nullable = false)
    private int scannedCount;

    @Column(name = "imported_count", nullable = false)
    private int importedCount;

    @Column(name = "item_limit", nullable = false, updatable = false)
    private int itemLimit;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "last_error", length = 100)
    private String lastError;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "requester_name", nullable = false, length = 120)
    private String requesterName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected SourceSyncJob() {
    }

    static SourceSyncJob historicalImport(
            UUID organizationId,
            UUID projectId,
            UUID sourceId,
            Instant windowStart,
            Instant windowEnd,
            int itemLimit,
            UUID requestedBy,
            String requesterName,
            Instant now
    ) {
        SourceSyncJob job = new SourceSyncJob();
        job.id = UUID.randomUUID();
        job.organizationId = organizationId;
        job.projectId = projectId;
        job.sourceId = sourceId;
        job.type = Type.HISTORICAL_IMPORT;
        job.status = Status.PENDING;
        job.windowStart = windowStart;
        job.windowEnd = windowEnd;
        job.providerCursor = SyncCursor.START.toString();
        job.itemLimit = itemLimit;
        job.nextAttemptAt = now;
        job.requestedBy = requestedBy;
        job.requesterName = requesterName;
        job.createdAt = now;
        return job;
    }

    /**
     * A poll of a source whose schedule came round. It has no requester, because nobody
     * asked, and no item limit, because a poll reads whatever the window holds.
     */
    static SourceSyncJob poll(
            UUID organizationId,
            UUID projectId,
            UUID sourceId,
            Instant windowStart,
            Instant windowEnd,
            Instant now
    ) {
        SourceSyncJob job = new SourceSyncJob();
        job.id = UUID.randomUUID();
        job.organizationId = organizationId;
        job.projectId = projectId;
        job.sourceId = sourceId;
        job.type = Type.JIRA_POLL;
        job.status = Status.PENDING;
        job.windowStart = windowStart;
        job.windowEnd = windowEnd;
        job.providerCursor = SyncCursor.START.toString();
        job.itemLimit = Integer.MAX_VALUE;
        job.nextAttemptAt = now;
        job.createdAt = now;
        return job;
    }

    void claim(Instant now) {
        this.status = Status.RUNNING;
        this.attempts++;
        this.claimedAt = now;
    }

    // True only for the worker holding the current claim.
    boolean isClaimedAt(Instant claim) {
        return status == Status.RUNNING && claim.equals(claimedAt);
    }

    /** Saves how far the import got; a failed page is tried again from here. */
    void advance(SyncCursor cursor, int scanned, int imported) {
        this.providerCursor = cursor.toString();
        this.scannedCount += scanned;
        this.importedCount += imported;
    }

    void complete(Instant now) {
        finish(Status.COMPLETED, null, now);
    }

    /** Stops at the item limit; resuming continues from the cursor. */
    void stopAtLimit(Instant now) {
        finish(Status.PARTIAL, null, now);
    }

    void fail(String error, Instant now) {
        finish(Status.FAILED, error, now);
    }

    void retryLater(String error, Instant nextAttemptAt) {
        this.status = Status.RETRY_SCHEDULED;
        this.lastError = error;
        this.nextAttemptAt = nextAttemptAt;
    }

    /** Continues a limited or failed import from its cursor, with a fresh item count and attempts. */
    void resume(Instant now) {
        this.status = Status.PENDING;
        this.importedCount = 0;
        this.attempts = 0;
        this.lastError = null;
        this.nextAttemptAt = now;
        this.completedAt = null;
    }

    boolean isResumable() {
        return status == Status.PARTIAL || status == Status.FAILED;
    }

    boolean limitReached() {
        return importedCount >= itemLimit;
    }

    private void finish(Status finalStatus, String error, Instant now) {
        this.status = finalStatus;
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

    UUID getSourceId() {
        return sourceId;
    }

    Status getStatus() {
        return status;
    }

    Instant getWindowStart() {
        return windowStart;
    }

    Instant getWindowEnd() {
        return windowEnd;
    }

    Type getType() {
        return type;
    }

    SyncCursor getCursor() {
        return SyncCursor.parse(providerCursor);
    }

    int getScannedCount() {
        return scannedCount;
    }

    int getImportedCount() {
        return importedCount;
    }

    int getItemLimit() {
        return itemLimit;
    }

    int getAttempts() {
        return attempts;
    }

    Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    String getLastError() {
        return lastError;
    }

    String getRequesterName() {
        return requesterName;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getCompletedAt() {
        return completedAt;
    }

    enum Type {
        /** An administrator asked for what the provider recorded before it was connected. */
        HISTORICAL_IMPORT,
        /** A polled source's schedule came round; nobody asked. */
        JIRA_POLL
    }

    enum Status {
        PENDING,
        RUNNING,
        RETRY_SCHEDULED,
        COMPLETED,
        PARTIAL,
        FAILED
    }
}
