package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.source.SourceType;
import com.hoangluongtran0309.releaseflow.source.WebhookAuthMode;

/**
 * A source to connect: a GitHub repository, a GitLab project on an allowed instance, a
 * Linear team, or a Jira project. Which fields are required depends on the type, so they are checked
 * together by {@link IntegrationSourceRequestValidator}.
 */
@ValidIntegrationSourceRequest
public class IntegrationSourceRequest {

    private SourceType type = SourceType.GITHUB;

    private String owner;

    private String repository;

    private String projectPath;

    private String apiBaseUrl;

    private WebhookAuthMode webhookAuthMode = WebhookAuthMode.GITLAB_SIGNING_TOKEN;

    private String teamId;

    // Linear mints its own webhook secret, so an administrator pastes it in.
    private String webhookSecret;

    private String apiToken;

    private String projectKey;

    private String siteUrl;

    private String accountEmail;

    public SourceType getType() {
        return type;
    }

    // An omitted type means GitHub, the type that existed before there was a choice.
    public void setType(SourceType type) {
        this.type = type == null ? SourceType.GITHUB : type;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner == null ? null : owner.strip();
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository == null ? null : repository.strip();
    }

    public String getProjectPath() {
        return projectPath;
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath == null ? null : projectPath.strip();
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl == null ? null : apiBaseUrl.strip();
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId == null ? null : teamId.strip();
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret == null ? null : webhookSecret.strip();
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken == null ? null : apiToken.strip();
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey == null ? null : projectKey.strip();
    }

    public String getSiteUrl() {
        return siteUrl;
    }

    public void setSiteUrl(String siteUrl) {
        this.siteUrl = siteUrl == null ? null : siteUrl.strip();
    }

    public String getAccountEmail() {
        return accountEmail;
    }

    public void setAccountEmail(String accountEmail) {
        this.accountEmail = accountEmail == null ? null : accountEmail.strip();
    }

    public WebhookAuthMode getWebhookAuthMode() {
        return webhookAuthMode;
    }

    // Standard Webhooks is what a current GitLab sends unless the instance was told otherwise.
    public void setWebhookAuthMode(WebhookAuthMode webhookAuthMode) {
        this.webhookAuthMode = webhookAuthMode == null ? WebhookAuthMode.GITLAB_SIGNING_TOKEN : webhookAuthMode;
    }

    // Keeps the pasted secret and token out of logs and error pages.
    @Override
    public String toString() {
        return "IntegrationSourceRequest[type=%s, owner=%s, repository=%s, projectPath=%s, apiBaseUrl=%s, "
                .formatted(type, owner, repository, projectPath, apiBaseUrl)
                + "teamId=%s, projectKey=%s, siteUrl=%s, accountEmail=%s, webhookSecret=***, apiToken=***]"
                        .formatted(teamId, projectKey, siteUrl, accountEmail);
    }
}
