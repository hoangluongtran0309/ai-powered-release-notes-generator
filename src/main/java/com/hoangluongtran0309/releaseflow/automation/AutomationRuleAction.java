package com.hoangluongtran0309.releaseflow.automation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One step of a Rule. Its position fixes the order the worker walks; the audience
 * and language choose which of a release's notes it delivers. A secret is stored
 * encrypted, bound to this Action, and never read back out to anyone.
 */
@Entity
@Table(name = "automation_rule_actions")
class AutomationRuleAction {

    @Id
    private UUID id;

    @Column(name = "rule_id", nullable = false, updatable = false)
    private UUID ruleId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 32, updatable = false)
    private ActionType actionType;

    @Column(name = "audience_id", nullable = false)
    private UUID audienceId;

    @Column(name = "target_language", nullable = false, length = 16)
    private String targetLanguage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> configuration;

    @Column(name = "secret_nonce")
    private byte[] secretNonce;

    @Column(name = "secret_ciphertext")
    private byte[] secretCiphertext;

    protected AutomationRuleAction() {
    }

    AutomationRuleAction(
            UUID id,
            UUID ruleId,
            UUID organizationId,
            int position,
            ActionType actionType,
            UUID audienceId,
            String targetLanguage,
            Map<String, String> configuration,
            AutomationSecrets.Secret secret
    ) {
        this.id = id;
        this.ruleId = ruleId;
        this.organizationId = organizationId;
        this.position = position;
        this.actionType = actionType;
        this.audienceId = audienceId;
        this.targetLanguage = targetLanguage;
        this.configuration = new LinkedHashMap<>(configuration);
        this.secretNonce = secret == null ? null : secret.nonce();
        this.secretCiphertext = secret == null ? null : secret.ciphertext();
    }

    /**
     * Rewrites the step in place, so an Action keeps its identity across an edit and
     * the secret bound to it goes on decrypting. Its kind never changes: an Action of
     * another kind is another Action.
     */
    void update(int position, UUID audienceId, String targetLanguage, Map<String, String> configuration,
            AutomationSecrets.Secret secret) {
        this.position = position;
        this.audienceId = audienceId;
        this.targetLanguage = targetLanguage;
        this.configuration = new LinkedHashMap<>(configuration);
        this.secretNonce = secret == null ? null : secret.nonce();
        this.secretCiphertext = secret == null ? null : secret.ciphertext();
    }

    boolean hasSecret() {
        return secretNonce != null && secretCiphertext != null;
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

    int getPosition() {
        return position;
    }

    ActionType getActionType() {
        return actionType;
    }

    UUID getAudienceId() {
        return audienceId;
    }

    String getTargetLanguage() {
        return targetLanguage;
    }

    Map<String, String> getConfiguration() {
        return Map.copyOf(configuration);
    }

    AutomationSecrets.Secret getSecret() {
        return hasSecret() ? new AutomationSecrets.Secret(secretNonce, secretCiphertext) : null;
    }
}
