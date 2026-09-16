package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.source.SourceType;
import com.hoangluongtran0309.releaseflow.source.WebhookAuthMode;

/**
 * A source to connect: a GitHub repository, or a GitLab project on an allowed instance.
 * Which fields are required depends on the type, so they are checked together by
 * {@link IntegrationSourceRequestValidator}.
 */
@ValidIntegrationSourceRequest
public class IntegrationSourceRequest {

    private SourceType type = SourceType.GITHUB;

    private String owner;

    private String repository;

    private String projectPath;

    private String apiBaseUrl;

    private WebhookAuthMode webhookAuthMode = WebhookAuthMode.GITLAB_SIGNING_TOKEN;

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

    public WebhookAuthMode getWebhookAuthMode() {
        return webhookAuthMode;
    }

    // Standard Webhooks is what a current GitLab sends unless the instance was told otherwise.
    public void setWebhookAuthMode(WebhookAuthMode webhookAuthMode) {
        this.webhookAuthMode = webhookAuthMode == null ? WebhookAuthMode.GITLAB_SIGNING_TOKEN : webhookAuthMode;
    }
}
