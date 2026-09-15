package com.hoangluongtran0309.releaseflow.project;

import java.time.Instant;
import java.util.UUID;

public final class IntegrationSourceCreated {

    private final UUID id;
    private final UUID projectId;
    private final SourceType type;
    private final String owner;
    private final String repository;
    private final UUID webhookId;
    private final String webhookPath;
    private final String webhookSecret;
    private final Instant createdAt;

    IntegrationSourceCreated(
            UUID id,
            UUID projectId,
            SourceType type,
            String owner,
            String repository,
            UUID webhookId,
            String webhookPath,
            String webhookSecret,
            Instant createdAt
    ) {
        this.id = id;
        this.projectId = projectId;
        this.type = type;
        this.owner = owner;
        this.repository = repository;
        this.webhookId = webhookId;
        this.webhookPath = webhookPath;
        this.webhookSecret = webhookSecret;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public SourceType getType() {
        return type;
    }

    public String getOwner() {
        return owner;
    }

    public String getRepository() {
        return repository;
    }

    public UUID getWebhookId() {
        return webhookId;
    }

    public String getWebhookPath() {
        return webhookPath;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "IntegrationSourceCreated[id=%s, projectId=%s, owner=%s, repository=%s, webhookId=%s, webhookSecret=[REDACTED]]"
                .formatted(id, projectId, owner, repository, webhookId);
    }
}
