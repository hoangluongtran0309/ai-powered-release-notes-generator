package com.hoangluongtran0309.releaseflow.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Where a Project's changes come from: for now a GitHub repository, with its webhook
 * signing secret and an optional access token. A Project may have several sources.
 */
@Entity
@Table(name = "integration_sources")
class IntegrationSource {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20, updatable = false)
    private SourceType sourceType;

    @Column(name = "external_project_key", nullable = false, length = 200, updatable = false)
    private String externalProjectKey;

    @Column(name = "repository_owner", nullable = false, length = 39)
    private String repositoryOwner;

    @Column(name = "repository_name", nullable = false, length = 100)
    private String repositoryName;

    @Column(name = "webhook_id", nullable = false, unique = true)
    private UUID webhookId;

    @Column(name = "secret_nonce", nullable = false, columnDefinition = "bytea")
    private byte[] secretNonce;

    @Column(name = "secret_ciphertext", nullable = false, columnDefinition = "bytea")
    private byte[] secretCiphertext;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_delivery_at")
    private Instant lastDeliveryAt;

    @Column(name = "token_nonce", columnDefinition = "bytea")
    private byte[] tokenNonce;

    @Column(name = "token_ciphertext", columnDefinition = "bytea")
    private byte[] tokenCiphertext;

    @Column(name = "token_updated_at")
    private Instant tokenUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 10)
    private ConnectionStatus connectionStatus;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    protected IntegrationSource() {
    }

    IntegrationSource(
            UUID id,
            UUID organizationId,
            UUID projectId,
            String repositoryOwner,
            String repositoryName,
            UUID webhookId,
            byte[] secretNonce,
            byte[] secretCiphertext,
            Instant createdAt
    ) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.sourceType = SourceType.GITHUB;
        this.externalProjectKey = repositoryOwner + "/" + repositoryName;
        this.repositoryOwner = repositoryOwner;
        this.repositoryName = repositoryName;
        this.webhookId = webhookId;
        this.secretNonce = secretNonce.clone();
        this.secretCiphertext = secretCiphertext.clone();
        this.createdAt = createdAt;
        this.connectionStatus = ConnectionStatus.ACTIVE;
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

    SourceType getSourceType() {
        return sourceType;
    }

    String getExternalProjectKey() {
        return externalProjectKey;
    }

    String getRepositoryOwner() {
        return repositoryOwner;
    }

    String getRepositoryName() {
        return repositoryName;
    }

    UUID getWebhookId() {
        return webhookId;
    }

    byte[] getSecretNonce() {
        return secretNonce.clone();
    }

    byte[] getSecretCiphertext() {
        return secretCiphertext.clone();
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getLastDeliveryAt() {
        return lastDeliveryAt;
    }

    ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    Instant getLastSyncAt() {
        return lastSyncAt;
    }

    String getLastErrorCode() {
        return lastErrorCode;
    }

    // A token GitHub accepted puts a source that failed to connect back in order.
    void replaceToken(CredentialCipher.EncryptedSecret token, Instant updatedAt) {
        this.tokenNonce = token.nonce();
        this.tokenCiphertext = token.ciphertext();
        this.tokenUpdatedAt = updatedAt;
        this.connectionStatus = ConnectionStatus.ACTIVE;
        this.lastErrorCode = null;
    }

    /** The outcome of reading the source's history; a refused credential marks the connection as failing. */
    void recordSync(Instant at, String errorCode, boolean credentialRejected) {
        this.lastSyncAt = at;
        this.lastErrorCode = errorCode;
        if (credentialRejected) {
            this.connectionStatus = ConnectionStatus.ERROR;
        }
    }

    boolean hasAccessToken() {
        return tokenCiphertext != null;
    }

    CredentialCipher.EncryptedSecret getToken() {
        return new CredentialCipher.EncryptedSecret(tokenNonce, tokenCiphertext);
    }

    Instant getTokenUpdatedAt() {
        return tokenUpdatedAt;
    }
}
