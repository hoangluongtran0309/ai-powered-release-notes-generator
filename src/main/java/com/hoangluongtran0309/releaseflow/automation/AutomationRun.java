package com.hoangluongtran0309.releaseflow.automation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One firing of one Rule against one Release. It snapshots the Rule's name and the
 * release version, so archiving or renaming the Rule never rewrites the history. Its
 * status is derived from its Actions: the first one that fails or ends unknown stops
 * the rest, and a cancellation waits for the Action already running.
 */
@Entity
@Table(name = "automation_runs")
class AutomationRun {

    @Id
    private UUID id;

    @Column(name = "rule_id", nullable = false, updatable = false)
    private UUID ruleId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "release_id", nullable = false, updatable = false)
    private UUID releaseId;

    @Column(name = "rule_name_snapshot", nullable = false, length = 120, updatable = false)
    private String ruleNameSnapshot;

    @Column(name = "release_version_snapshot", nullable = false, length = 50, updatable = false)
    private String releaseVersionSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 32, updatable = false)
    private TriggerType triggerType;

    @Column(name = "request_id", updatable = false)
    private UUID requestId;

    @Column(name = "initiated_by", updatable = false)
    private UUID initiatedBy;

    @Column(name = "initiator_name", length = 120, updatable = false)
    private String initiatorName;

    @Column(name = "scheduled_for", updatable = false)
    private Instant scheduledFor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExecutionStatus status;

    @Column(name = "cancellation_requested", nullable = false)
    private boolean cancellationRequested;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AutomationRun() {
    }

    AutomationRun(
            UUID id,
            AutomationRule rule,
            UUID projectId,
            UUID releaseId,
            String releaseVersion,
            TriggerType triggerType,
            UUID requestId,
            UUID initiatedBy,
            String initiatorName,
            Instant scheduledFor,
            Instant createdAt
    ) {
        this.id = id;
        this.ruleId = rule.getId();
        this.organizationId = rule.getOrganizationId();
        this.projectId = projectId;
        this.releaseId = releaseId;
        this.ruleNameSnapshot = rule.getName();
        this.releaseVersionSnapshot = releaseVersion;
        this.triggerType = triggerType;
        this.requestId = requestId;
        this.initiatedBy = initiatedBy;
        this.initiatorName = initiatorName;
        this.scheduledFor = scheduledFor;
        this.status = ExecutionStatus.PENDING;
        this.createdAt = createdAt;
    }

    void actionClaimed(Instant now) {
        this.status = ExecutionStatus.RUNNING;
        if (startedAt == null) {
            this.startedAt = now;
        }
        this.completedAt = null;
    }

    /**
     * Recomputes the status from the Actions. An unknown outcome outranks a failure,
     * because it needs a person either way, and both stop the Actions behind them.
     */
    void settle(List<AutomationActionRun> actions, Instant now) {
        if (actions.stream().anyMatch(action -> action.getStatus() == ExecutionStatus.UNKNOWN)) {
            finish(ExecutionStatus.UNKNOWN, now);
        } else if (actions.stream().anyMatch(action -> action.getStatus() == ExecutionStatus.FAILED)) {
            finish(ExecutionStatus.FAILED, now);
        } else if (actions.stream().anyMatch(action -> action.getStatus() == ExecutionStatus.CANCELLED)) {
            finish(ExecutionStatus.CANCELLED, now);
        } else if (actions.stream().allMatch(action -> action.getStatus() == ExecutionStatus.SUCCEEDED)) {
            finish(ExecutionStatus.SUCCEEDED, now);
        } else {
            this.status = ExecutionStatus.PENDING;
            this.completedAt = null;
        }
    }

    void requestCancellation() {
        this.cancellationRequested = true;
    }

    /** A retry reopens the Run for the one Action a person chose to send again. */
    void reopen() {
        this.status = ExecutionStatus.PENDING;
        this.cancellationRequested = false;
        this.completedAt = null;
    }

    private void finish(ExecutionStatus outcome, Instant now) {
        this.status = outcome;
        this.completedAt = now;
    }

    UUID getId() {
        return id;
    }

    UUID getRuleId() {
        return ruleId;
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

    String getRuleNameSnapshot() {
        return ruleNameSnapshot;
    }

    String getReleaseVersionSnapshot() {
        return releaseVersionSnapshot;
    }

    TriggerType getTriggerType() {
        return triggerType;
    }

    UUID getRequestId() {
        return requestId;
    }

    String getInitiatorName() {
        return initiatorName;
    }

    /** The occurrence this Run answers, for a Rule ReleaseFlow itself set off. */
    Instant getScheduledFor() {
        return scheduledFor;
    }

    ExecutionStatus getStatus() {
        return status;
    }

    boolean isCancellationRequested() {
        return cancellationRequested;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getStartedAt() {
        return startedAt;
    }

    Instant getCompletedAt() {
        return completedAt;
    }
}
