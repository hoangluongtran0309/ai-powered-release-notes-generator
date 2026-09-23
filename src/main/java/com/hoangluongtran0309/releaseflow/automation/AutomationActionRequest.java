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
    private String parentPageId;
    private String siteUrl;
    private String email;
    private String spaceId;
    private String subdomain;
    private String clientId;
    private String sectionId;
    private String userSegmentId;
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

    public String getParentPageId() {
        return parentPageId;
    }

    public void setParentPageId(String parentPageId) {
        this.parentPageId = parentPageId == null || parentPageId.isBlank() ? null : parentPageId.strip();
    }

    public String getSiteUrl() {
        return siteUrl;
    }

    public void setSiteUrl(String siteUrl) {
        this.siteUrl = siteUrl == null || siteUrl.isBlank() ? null : siteUrl.strip();
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null || email.isBlank() ? null : email.strip();
    }

    public String getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(String spaceId) {
        this.spaceId = spaceId == null || spaceId.isBlank() ? null : spaceId.strip();
    }

    public String getSubdomain() {
        return subdomain;
    }

    public void setSubdomain(String subdomain) {
        this.subdomain = subdomain == null || subdomain.isBlank() ? null : subdomain.strip();
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId == null || clientId.isBlank() ? null : clientId.strip();
    }

    public String getSectionId() {
        return sectionId;
    }

    public void setSectionId(String sectionId) {
        this.sectionId = sectionId == null || sectionId.isBlank() ? null : sectionId.strip();
    }

    public String getUserSegmentId() {
        return userSegmentId;
    }

    public void setUserSegmentId(String userSegmentId) {
        this.userSegmentId = userSegmentId == null || userSegmentId.isBlank() ? null : userSegmentId.strip();
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret == null || secret.isBlank() ? null : secret.strip();
    }

    /** A row the person left empty, which the form always offers a few of. */
    boolean isBlank() {
        return actionType == null && audienceId == null && recipients == null && secret == null
                && parentPageId == null && siteUrl == null && email == null && spaceId == null
                && subdomain == null && clientId == null && sectionId == null && userSegmentId == null;
    }
}
