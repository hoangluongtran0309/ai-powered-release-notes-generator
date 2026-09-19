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
 * One Organization's standing instruction: when this trigger fires, deliver the
 * release note through these Actions, in order. A Rule without a Project watches
 * every Project of the Organization. It starts disabled, because enabling it is
 * what proves each Action can be carried out. Archiving keeps it only for the Runs
 * it already made; it never fires again and frees its name.
 */
@Entity
@Table(name = "automation_rules")
class AutomationRule {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 32)
    private TriggerType triggerType;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AutomationRule() {
    }

    AutomationRule(UUID id, UUID organizationId, String name, TriggerType triggerType, UUID projectId, Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.name = name;
        this.triggerType = triggerType;
        this.projectId = projectId;
        this.enabled = false;
        this.active = true;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    void redefine(String name, TriggerType triggerType, UUID projectId, Instant at) {
        requireActive();
        this.name = name;
        this.triggerType = triggerType;
        this.projectId = projectId;
        this.updatedAt = at;
    }

    void enable(Instant at) {
        requireActive();
        this.enabled = true;
        this.updatedAt = at;
    }

    void disable(Instant at) {
        requireActive();
        this.enabled = false;
        this.updatedAt = at;
    }

    void archive(Instant at) {
        if (!active) {
            return;
        }
        this.enabled = false;
        this.active = false;
        this.updatedAt = at;
    }

    /** Whether this Rule answers an event of that trigger from that Project. */
    boolean matches(UUID eventProjectId, TriggerType eventTrigger) {
        return enabled && active && triggerType == eventTrigger
                && (projectId == null || projectId.equals(eventProjectId));
    }

    private void requireActive() {
        if (!active) {
            throw AutomationConflictException.ruleArchived();
        }
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

    String getName() {
        return name;
    }

    TriggerType getTriggerType() {
        return triggerType;
    }

    boolean isEnabled() {
        return enabled;
    }

    boolean isActive() {
        return active;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
