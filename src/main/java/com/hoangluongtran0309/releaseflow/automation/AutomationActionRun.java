package com.hoangluongtran0309.releaseflow.automation;

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
 * One Action of one Run, with everything the delivery needs frozen when the Run was
 * created: the note, the configuration, and the encrypted secret. The note is absent
 * when the Release had none for that audience and language; the Action then fails,
 * which is how a broken Rule stays out of the publication that triggered it. An
 * Action that ends {@code UNKNOWN} is never sent again without a person saying so.
 */
@Entity
@Table(name = "automation_action_runs")
class AutomationActionRun {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "rule_action_id", nullable = false, updatable = false)
    private UUID ruleActionId;

    @Column(nullable = false, updatable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 32, updatable = false)
    private ActionType actionType;

    @Column(name = "audience_id", nullable = false, updatable = false)
    private UUID audienceId;

    @Column(name = "audience_name_snapshot", nullable = false, length = 120, updatable = false)
    private String audienceNameSnapshot;

    @Column(name = "language_snapshot", nullable = false, length = 16, updatable = false)
    private String languageSnapshot;

    @Column(name = "note_content_snapshot", columnDefinition = "text", updatable = false)
    private String noteContentSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration_snapshot", nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, String> configurationSnapshot;

    @Column(name = "secret_nonce_snapshot", updatable = false)
    private byte[] secretNonceSnapshot;

    @Column(name = "secret_ciphertext_snapshot", updatable = false)
    private byte[] secretCiphertextSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExecutionStatus status;

    @Column(name = "external_reference", length = 1024)
    private String externalReference;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AutomationActionRun() {
    }

    AutomationActionRun(
            UUID id,
            UUID runId,
            UUID organizationId,
            UUID ruleActionId,
            int position,
            ActionType actionType,
            UUID audienceId,
            String audienceName,
            String language,
            String noteContent,
            Map<String, String> configuration,
            AutomationSecrets.Secret secret
    ) {
        this.id = id;
        this.runId = runId;
        this.organizationId = organizationId;
        this.ruleActionId = ruleActionId;
        this.position = position;
        this.actionType = actionType;
        this.audienceId = audienceId;
        this.audienceNameSnapshot = audienceName;
        this.languageSnapshot = language;
        this.noteContentSnapshot = noteContent;
        this.configurationSnapshot = new LinkedHashMap<>(configuration);
        this.secretNonceSnapshot = secret == null ? null : secret.nonce();
        this.secretCiphertextSnapshot = secret == null ? null : secret.ciphertext();
        this.status = ExecutionStatus.PENDING;
        this.attempts = 0;
    }

    void claim(Instant now) {
        this.status = ExecutionStatus.RUNNING;
        this.claimedAt = now;
        this.startedAt = now;
        this.completedAt = null;
        this.errorCode = null;
        this.attempts++;
    }

    /** True only for the worker still holding the current claim. */
    boolean isClaimedAt(Instant claim) {
        return status == ExecutionStatus.RUNNING && claim.equals(claimedAt);
    }

    void succeed(String externalReference, Instant now) {
        this.status = ExecutionStatus.SUCCEEDED;
        this.externalReference = externalReference;
        this.errorCode = null;
        this.completedAt = now;
    }

    void fail(String errorCode, Instant now) {
        this.status = ExecutionStatus.FAILED;
        this.errorCode = errorCode;
        this.completedAt = now;
    }

    void markUnknown(String errorCode, Instant now) {
        this.status = ExecutionStatus.UNKNOWN;
        this.errorCode = errorCode;
        this.completedAt = now;
    }

    void retry() {
        this.status = ExecutionStatus.PENDING;
        this.errorCode = null;
        this.claimedAt = null;
        this.completedAt = null;
    }

    void cancelPending(Instant now) {
        if (status == ExecutionStatus.PENDING) {
            this.status = ExecutionStatus.CANCELLED;
            this.completedAt = now;
        }
    }

    UUID getId() {
        return id;
    }

    UUID getRunId() {
        return runId;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getRuleActionId() {
        return ruleActionId;
    }

    int getPosition() {
        return position;
    }

    ActionType getActionType() {
        return actionType;
    }

    UUID getAudienceId() {
        return audienceId;
    }

    String getAudienceNameSnapshot() {
        return audienceNameSnapshot;
    }

    String getLanguageSnapshot() {
        return languageSnapshot;
    }

    String getNoteContentSnapshot() {
        return noteContentSnapshot;
    }

    Map<String, String> getConfigurationSnapshot() {
        return Map.copyOf(configurationSnapshot);
    }

    AutomationSecrets.Secret getSecretSnapshot() {
        return secretNonceSnapshot == null || secretCiphertextSnapshot == null
                ? null
                : new AutomationSecrets.Secret(secretNonceSnapshot, secretCiphertextSnapshot);
    }

    ExecutionStatus getStatus() {
        return status;
    }

    String getExternalReference() {
        return externalReference;
    }

    String getErrorCode() {
        return errorCode;
    }

    int getAttempts() {
        return attempts;
    }

    Instant getStartedAt() {
        return startedAt;
    }

    Instant getCompletedAt() {
        return completedAt;
    }
}
