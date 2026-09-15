package com.hoangluongtran0309.releaseflow.translation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One change's content to translate into one language. Its input never changes: an
 * edited summary has another hash and gets another job. It is worked outside any
 * transaction that other work depends on, and each claim runs in its own short one.
 */
@Entity
@Table(name = "translation_jobs")
class TranslationJob {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "change_id", nullable = false, updatable = false)
    private UUID changeId;

    @Column(name = "source_language", nullable = false, length = 16, updatable = false)
    private String sourceLanguage;

    @Column(name = "target_language", nullable = false, length = 16, updatable = false)
    private String targetLanguage;

    @Column(name = "input_hash", nullable = false, length = 64, updatable = false)
    private String inputHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, String> input;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> output;

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

    protected TranslationJob() {
    }

    TranslationJob(
            UUID organizationId,
            UUID projectId,
            UUID changeId,
            String sourceLanguage,
            String targetLanguage,
            String inputHash,
            Map<String, String> input,
            Instant createdAt
    ) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.changeId = changeId;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
        this.inputHash = inputHash;
        this.input = new LinkedHashMap<>(input);
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

    // True only for the worker holding the current claim.
    boolean isClaimedAt(Instant claim) {
        return status == Status.RUNNING && claim.equals(claimedAt);
    }

    void succeed(Map<String, String> translated, Instant now) {
        this.status = Status.SUCCEEDED;
        this.output = new LinkedHashMap<>(translated);
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

    /** A failed job starts over with a fresh budget of attempts. */
    void restart(Instant now) {
        if (status != Status.FAILED) {
            return;
        }
        this.status = Status.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
        this.completedAt = null;
    }

    TranslationState state() {
        return switch (status) {
            case SUCCEEDED -> TranslationState.ready(output);
            case FAILED -> TranslationState.failed();
            case PENDING, RUNNING -> TranslationState.pending();
        };
    }

    UUID getId() {
        return id;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getChangeId() {
        return changeId;
    }

    String getSourceLanguage() {
        return sourceLanguage;
    }

    String getTargetLanguage() {
        return targetLanguage;
    }

    Map<String, String> getInput() {
        return Map.copyOf(input);
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
