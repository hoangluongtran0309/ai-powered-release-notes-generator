package com.hoangluongtran0309.releaseflow.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "github_integrations")
class GitHubIntegration {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

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

    protected GitHubIntegration() {
    }

    GitHubIntegration(
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
        this.repositoryOwner = repositoryOwner;
        this.repositoryName = repositoryName;
        this.webhookId = webhookId;
        this.secretNonce = secretNonce.clone();
        this.secretCiphertext = secretCiphertext.clone();
        this.createdAt = createdAt;
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

    void replaceToken(CredentialCipher.EncryptedSecret token, Instant updatedAt) {
        this.tokenNonce = token.nonce();
        this.tokenCiphertext = token.ciphertext();
        this.tokenUpdatedAt = updatedAt;
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
