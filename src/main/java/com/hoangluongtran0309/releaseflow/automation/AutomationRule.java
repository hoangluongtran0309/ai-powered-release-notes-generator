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

    @Column(name = "trigger_release_id")
    private UUID triggerReleaseId;

    @Column(name = "cron_expression", length = 255)
    private String cronExpression;

    @Column(name = "cron_time_zone", length = 64)
    private String cronTimeZone;

    @Column(name = "reminder_days_before")
    private Integer reminderDaysBefore;

    @Column(name = "webhook_id")
    private UUID webhookId;

    @Column(name = "webhook_secret_nonce")
    private byte[] webhookSecretNonce;

    @Column(name = "webhook_secret_ciphertext")
    private byte[] webhookSecretCiphertext;

    @Column(name = "next_fire_at")
    private Instant nextFireAt;

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

    /** A trigger that fires on an event of its own needs nothing else remembered. */
    void configureWithoutParameters(Instant at) {
        requireActive();
        clearTriggerConfiguration();
        this.updatedAt = at;
    }

    /**
     * The Release a cron Rule repeats, in the civil time zone the schedule is read in.
     * The Project follows the Release, because that is the Project the Rule works on.
     */
    void configureCron(UUID releaseId, UUID projectId, String expression, String timeZone, Instant at) {
        requireActive();
        clearTriggerConfiguration();
        this.triggerReleaseId = releaseId;
        this.projectId = projectId;
        this.cronExpression = expression;
        this.cronTimeZone = timeZone;
        this.updatedAt = at;
    }

    void configureReminder(int daysBefore, Instant at) {
        requireActive();
        clearTriggerConfiguration();
        this.reminderDaysBefore = daysBefore;
        this.updatedAt = at;
    }

    void configureWebhook(UUID webhookId, AutomationSecrets.Secret secret, Instant at) {
        requireActive();
        clearTriggerConfiguration();
        this.webhookId = webhookId;
        this.webhookSecretNonce = secret.nonce();
        this.webhookSecretCiphertext = secret.ciphertext();
        this.updatedAt = at;
    }

    /** A new secret over the same path: callers keep the URL, the old secret dies now. */
    void rotateWebhookSecret(AutomationSecrets.Secret secret, Instant at) {
        requireActive();
        if (triggerType != TriggerType.EXTERNAL_WEBHOOK || webhookId == null) {
            throw AutomationConflictException.webhookRuleRequired();
        }
        this.webhookSecretNonce = secret.nonce();
        this.webhookSecretCiphertext = secret.ciphertext();
        this.updatedAt = at;
    }

    /**
     * Books the next firing. It is always computed from the present rather than from
     * the occurrence just handled, so a Rule that was down for a week owes one catch-up
     * Run and not a week of them.
     */
    void advanceNextFireAt(Instant nextFireAt, Instant at) {
        this.nextFireAt = nextFireAt;
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
        this.nextFireAt = null;
        this.updatedAt = at;
    }

    void archive(Instant at) {
        if (!active) {
            return;
        }
        this.enabled = false;
        this.active = false;
        this.nextFireAt = null;
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

    // Changing the trigger forgets what only the previous one meant, so no Rule ever
    // carries a schedule or a webhook secret its trigger no longer uses.
    private void clearTriggerConfiguration() {
        this.triggerReleaseId = null;
        this.cronExpression = null;
        this.cronTimeZone = null;
        this.reminderDaysBefore = null;
        this.webhookId = null;
        this.webhookSecretNonce = null;
        this.webhookSecretCiphertext = null;
        this.nextFireAt = null;
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

    UUID getTriggerReleaseId() {
        return triggerReleaseId;
    }

    String getCronExpression() {
        return cronExpression;
    }

    String getCronTimeZone() {
        return cronTimeZone;
    }

    Integer getReminderDaysBefore() {
        return reminderDaysBefore;
    }

    UUID getWebhookId() {
        return webhookId;
    }

    /** The encrypted webhook secret, or null when this Rule is not called from outside. */
    AutomationSecrets.Secret getWebhookSecret() {
        return webhookSecretNonce == null || webhookSecretCiphertext == null
                ? null
                : new AutomationSecrets.Secret(webhookSecretNonce, webhookSecretCiphertext);
    }

    Instant getNextFireAt() {
        return nextFireAt;
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
