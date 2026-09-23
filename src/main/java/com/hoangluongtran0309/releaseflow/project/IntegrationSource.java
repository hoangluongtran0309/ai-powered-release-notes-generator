package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.source.SourceType;
import com.hoangluongtran0309.releaseflow.source.WebhookAuthMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Where a Project's changes come from: a GitHub repository, a GitLab project, or a Linear
 * team, with its webhook secret and an access token. A Project may have several sources.
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

    // Only a GitHub source has these; its ciphertexts are bound to them.
    @Column(name = "repository_owner", length = 39)
    private String repositoryOwner;

    @Column(name = "repository_name", length = 100)
    private String repositoryName;

    // Only a source whose instance the Organization chose has one, so far GitLab.
    @Column(name = "api_base_url", length = 255, updatable = false)
    private String apiBaseUrl;

    // Only a source whose projects live inside a workspace has one, so far Linear.
    @Column(name = "external_workspace_key", length = 100, updatable = false)
    private String externalWorkspaceKey;

    // Only a source whose credential names an account has one, so far Jira.
    @Column(name = "credential_identity", length = 255, updatable = false)
    private String credentialIdentity;

    // Only a polled source has a schedule; a delivered one is told instead of asking.
    @Column(name = "poll_cursor_at")
    private Instant pollCursorAt;

    @Column(name = "next_poll_at")
    private Instant nextPollAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "webhook_auth_mode", nullable = false, length = 30, updatable = false)
    private WebhookAuthMode webhookAuthMode;

    // A polled source is never delivered to, so it has no identity to be delivered at.
    @Column(name = "webhook_id", unique = true)
    private UUID webhookId;

    @Column(name = "secret_nonce", columnDefinition = "bytea")
    private byte[] secretNonce;

    @Column(name = "secret_ciphertext", columnDefinition = "bytea")
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

    private IntegrationSource(
            UUID id,
            UUID organizationId,
            UUID projectId,
            SourceType sourceType,
            String externalProjectKey,
            WebhookAuthMode webhookAuthMode,
            UUID webhookId,
            byte[] secretNonce,
            byte[] secretCiphertext,
            Instant createdAt
    ) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.sourceType = sourceType;
        this.externalProjectKey = externalProjectKey;
        this.webhookAuthMode = webhookAuthMode;
        this.webhookId = webhookId;
        this.secretNonce = secretNonce == null ? null : secretNonce.clone();
        this.secretCiphertext = secretCiphertext == null ? null : secretCiphertext.clone();
        this.createdAt = createdAt;
        this.connectionStatus = ConnectionStatus.ACTIVE;
    }

    static IntegrationSource gitHub(
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
        IntegrationSource source = new IntegrationSource(
                id,
                organizationId,
                projectId,
                SourceType.GITHUB,
                repositoryOwner + "/" + repositoryName,
                WebhookAuthMode.GITHUB_HMAC,
                webhookId,
                secretNonce,
                secretCiphertext,
                createdAt
        );
        source.repositoryOwner = repositoryOwner;
        source.repositoryName = repositoryName;
        return source;
    }

    /**
     * A Jira project. Nothing is delivered here, so there is no webhook identity and no
     * secret; instead the source carries the schedule ReleaseFlow reads it on.
     */
    static IntegrationSource jira(
            UUID id,
            UUID organizationId,
            UUID projectId,
            String projectKey,
            String siteUrl,
            String accountEmail,
            Instant createdAt,
            Instant firstPollAt
    ) {
        IntegrationSource source = new IntegrationSource(
                id,
                organizationId,
                projectId,
                SourceType.JIRA,
                projectKey,
                WebhookAuthMode.NONE,
                null,
                null,
                null,
                createdAt
        );
        source.apiBaseUrl = siteUrl;
        source.credentialIdentity = accountEmail;
        source.pollCursorAt = createdAt;
        source.nextPollAt = firstPollAt;
        return source;
    }

    /** A poll that finished moves the cursor to the window it covered and books the next one. */
    void recordPoll(Instant cursorAt, Instant nextPollAt, Instant at) {
        this.pollCursorAt = cursorAt;
        this.nextPollAt = nextPollAt;
        this.lastSyncAt = at;
        this.lastErrorCode = null;
        this.connectionStatus = ConnectionStatus.ACTIVE;
    }

    /** A poll that failed keeps its cursor, so nothing is skipped, and waits before asking again. */
    void recordPollFailure(String errorCode, Instant retryAt, Instant at) {
        this.lastSyncAt = at;
        this.lastErrorCode = errorCode;
        this.nextPollAt = retryAt;
        this.connectionStatus = ConnectionStatus.ERROR;
    }

    static IntegrationSource linear(
            UUID id,
            UUID organizationId,
            UUID projectId,
            String teamId,
            String workspaceId,
            UUID webhookId,
            byte[] secretNonce,
            byte[] secretCiphertext,
            Instant createdAt
    ) {
        IntegrationSource source = new IntegrationSource(
                id,
                organizationId,
                projectId,
                SourceType.LINEAR,
                teamId,
                WebhookAuthMode.LINEAR_HMAC,
                webhookId,
                secretNonce,
                secretCiphertext,
                createdAt
        );
        source.externalWorkspaceKey = workspaceId;
        return source;
    }

    static IntegrationSource gitLab(
            UUID id,
            UUID organizationId,
            UUID projectId,
            String projectPath,
            String apiBaseUrl,
            WebhookAuthMode webhookAuthMode,
            UUID webhookId,
            byte[] secretNonce,
            byte[] secretCiphertext,
            Instant createdAt
    ) {
        IntegrationSource source = new IntegrationSource(
                id,
                organizationId,
                projectId,
                SourceType.GITLAB,
                projectPath,
                webhookAuthMode,
                webhookId,
                secretNonce,
                secretCiphertext,
                createdAt
        );
        source.apiBaseUrl = apiBaseUrl;
        return source;
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

    String getApiBaseUrl() {
        return apiBaseUrl;
    }

    String getExternalWorkspaceKey() {
        return externalWorkspaceKey;
    }

    WebhookAuthMode getWebhookAuthMode() {
        return webhookAuthMode;
    }

    UUID getWebhookId() {
        return webhookId;
    }

    byte[] getSecretNonce() {
        return secretNonce == null ? null : secretNonce.clone();
    }

    byte[] getSecretCiphertext() {
        return secretCiphertext == null ? null : secretCiphertext.clone();
    }

    String getCredentialIdentity() {
        return credentialIdentity;
    }

    Instant getPollCursorAt() {
        return pollCursorAt;
    }

    Instant getNextPollAt() {
        return nextPollAt;
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

    // A token the provider accepted puts a source that failed to connect back in order.
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
