package com.hoangluongtran0309.releaseflow.automation;

import java.util.UUID;

/**
 * One step of a rule as a person or an API client describes it. The secret is only
 * ever written: leaving it empty on an update keeps the one already stored.
 */
public class AutomationActionRequest {

    private UUID id;
    private ActionType actionType;
    private UUID audienceId;
    private String language;
    private String recipients;
    private String secret;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
    }

    public UUID getAudienceId() {
        return audienceId;
    }

    public void setAudienceId(UUID audienceId) {
        this.audienceId = audienceId;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language == null || language.isBlank() ? null : language.strip();
    }

    public String getRecipients() {
        return recipients;
    }

    public void setRecipients(String recipients) {
        this.recipients = recipients == null || recipients.isBlank() ? null : recipients.strip();
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret == null || secret.isBlank() ? null : secret.strip();
    }

    /** A row the person left empty, which the form always offers a few of. */
    boolean isBlank() {
        return actionType == null && audienceId == null && recipients == null && secret == null;
    }
}
