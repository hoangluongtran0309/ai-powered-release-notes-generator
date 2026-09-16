package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.github.GitHubApiClient;
import com.hoangluongtran0309.releaseflow.gitlab.GitLabApiClient;
import com.hoangluongtran0309.releaseflow.gitlab.GitLabBaseUrl;
import com.hoangluongtran0309.releaseflow.source.ProviderAccess;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import com.hoangluongtran0309.releaseflow.source.WebhookAuthMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A Project's integration sources. Each source gets its own webhook ID and secret,
 * revealed once, and an optional write-only access token. Secrets and tokens are
 * encrypted with authenticated data that binds them to their source.
 */
@Service
public class IntegrationSourceService {

    private static final int WEBHOOK_SECRET_BYTES = 32;
    private static final String EXTERNAL_UNIQUE_CONSTRAINT = "integration_sources_external_unique";
    private static final String GITHUB_TOKEN_PURPOSE = "github-access-token";
    private static final String GITLAB_TOKEN_PURPOSE = "gitlab-access-token";
    private static final String WEBHOOK_DELIVERY = "WEBHOOK";

    private final ProjectRepository projectRepository;
    private final IntegrationSourceRepository sourceRepository;
    private final CredentialCipher credentialCipher;
    private final GitHubApiClient gitHubApiClient;
    private final GitLabApiClient gitLabApiClient;
    private final GitLabBaseUrl gitLabBaseUrl;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    IntegrationSourceService(
            ProjectRepository projectRepository,
            IntegrationSourceRepository sourceRepository,
            CredentialCipher credentialCipher,
            GitHubApiClient gitHubApiClient,
            GitLabApiClient gitLabApiClient,
            GitLabBaseUrl gitLabBaseUrl,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this(
                projectRepository,
                sourceRepository,
                credentialCipher,
                gitHubApiClient,
                gitLabApiClient,
                gitLabBaseUrl,
                transactionManager,
                clock,
                new SecureRandom()
        );
    }

    IntegrationSourceService(
            ProjectRepository projectRepository,
            IntegrationSourceRepository sourceRepository,
            CredentialCipher credentialCipher,
            GitHubApiClient gitHubApiClient,
            GitLabApiClient gitLabApiClient,
            GitLabBaseUrl gitLabBaseUrl,
            PlatformTransactionManager transactionManager,
            Clock clock,
            SecureRandom secureRandom
    ) {
        this.projectRepository = projectRepository;
        this.sourceRepository = sourceRepository;
        this.credentialCipher = credentialCipher;
        this.gitHubApiClient = gitHubApiClient;
        this.gitLabApiClient = gitLabApiClient;
        this.gitLabBaseUrl = gitLabBaseUrl;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    /**
     * Connects a repository or project to a Project. A given repository or project belongs
     * to at most one Project of the Organization; a Project may have several sources.
     */
    @Transactional
    public IntegrationSourceCreated create(UUID organizationId, UUID projectId, IntegrationSourceRequest request) {
        requireProject(organizationId, projectId);
        return switch (request.getType()) {
            case GITHUB -> createGitHub(organizationId, projectId, request);
            case GITLAB -> createGitLab(organizationId, projectId, request);
        };
    }

    private IntegrationSourceCreated createGitHub(
            UUID organizationId,
            UUID projectId,
            IntegrationSourceRequest request
    ) {
        String owner = canonicalize(request.getOwner());
        String repository = canonicalize(request.getRepository());
        String projectKey = owner + "/" + repository;
        requireNotConnected(organizationId, SourceType.GITHUB, projectKey);

        UUID sourceId = UUID.randomUUID();
        UUID webhookId = UUID.randomUUID();
        Instant createdAt = clock.instant();
        String webhookSecret = generateWebhookSecret();
        CredentialCipher.EncryptedSecret encryptedSecret = credentialCipher.encrypt(
                webhookSecret,
                gitHubAuthenticatedData(organizationId, projectId, sourceId, owner, repository)
        );

        IntegrationSource source = IntegrationSource.gitHub(
                sourceId,
                organizationId,
                projectId,
                owner,
                repository,
                webhookId,
                encryptedSecret.nonce(),
                encryptedSecret.ciphertext(),
                createdAt
        );
        save(source, SourceType.GITHUB);

        return new IntegrationSourceCreated(
                sourceId,
                projectId,
                SourceType.GITHUB,
                projectKey,
                owner,
                repository,
                null,
                WebhookAuthMode.GITHUB_HMAC,
                webhookId,
                webhookPath(SourceType.GITHUB, webhookId),
                webhookSecret,
                createdAt
        );
    }

    private IntegrationSourceCreated createGitLab(
            UUID organizationId,
            UUID projectId,
            IntegrationSourceRequest request
    ) {
        // The only address a request may choose, so the deployment's allowlist decides it.
        String apiBaseUrl = gitLabBaseUrl.validated(request.getApiBaseUrl());
        String projectPath = canonicalize(request.getProjectPath());
        requireNotConnected(organizationId, SourceType.GITLAB, projectPath);

        UUID sourceId = UUID.randomUUID();
        UUID webhookId = UUID.randomUUID();
        Instant createdAt = clock.instant();
        String webhookSecret = generateWebhookSecret();
        CredentialCipher.EncryptedSecret encryptedSecret = credentialCipher.encrypt(
                webhookSecret,
                gitLabAuthenticatedData(organizationId, projectId, sourceId, projectPath)
        );

        IntegrationSource source = IntegrationSource.gitLab(
                sourceId,
                organizationId,
                projectId,
                projectPath,
                apiBaseUrl,
                request.getWebhookAuthMode(),
                webhookId,
                encryptedSecret.nonce(),
                encryptedSecret.ciphertext(),
                createdAt
        );
        save(source, SourceType.GITLAB);

        return new IntegrationSourceCreated(
                sourceId,
                projectId,
                SourceType.GITLAB,
                projectPath,
                null,
                null,
                apiBaseUrl,
                request.getWebhookAuthMode(),
                webhookId,
                webhookPath(SourceType.GITLAB, webhookId),
                webhookSecret,
                createdAt
        );
    }

    @Transactional(readOnly = true)
    public List<IntegrationSourceView> list(UUID organizationId, UUID projectId) {
        requireProject(organizationId, projectId);
        return sourceRepository.findAllByOrganizationIdAndProjectIdOrderByCreatedAtAscIdAsc(organizationId, projectId)
                .stream()
                .map(IntegrationSourceService::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public IntegrationSourceView get(UUID organizationId, UUID projectId, UUID sourceId) {
        return view(find(organizationId, projectId, sourceId));
    }

    /**
     * Stores a new access token after the provider confirms it can read the project. The
     * provider call runs between two short transactions, never inside one.
     */
    public void replaceToken(UUID organizationId, UUID projectId, UUID sourceId, SourceTokenRequest request) {
        String token = request.getToken();
        Coordinates coordinates = transactionTemplate.execute(status -> {
            IntegrationSource source = find(organizationId, projectId, sourceId);
            return new Coordinates(
                    source.getSourceType(),
                    source.getExternalProjectKey(),
                    source.getApiBaseUrl(),
                    source.getRepositoryOwner(),
                    source.getRepositoryName()
            );
        });

        ProviderAccess access = switch (coordinates.sourceType()) {
            case GITHUB -> gitHubApiClient.checkPullRequestAccess(
                    coordinates.repositoryOwner(),
                    coordinates.repositoryName(),
                    token
            );
            case GITLAB -> gitLabApiClient.checkProjectAccess(
                    coordinates.apiBaseUrl(),
                    coordinates.externalProjectKey(),
                    token
            );
        };
        if (access == ProviderAccess.REJECTED) {
            throw new SourceTokenRejectedException(coordinates.sourceType());
        }
        if (access == ProviderAccess.UNAVAILABLE) {
            throw new SourceUnavailableException(coordinates.sourceType());
        }

        transactionTemplate.executeWithoutResult(status -> {
            IntegrationSource current = find(organizationId, projectId, sourceId);
            current.replaceToken(
                    credentialCipher.encrypt(token, tokenAuthenticatedData(current)),
                    clock.instant()
            );
        });
    }

    /**
     * Records how reading a source's history ended. A refused credential marks the
     * source as failing until a new token is saved.
     */
    @Transactional
    public void recordSync(UUID organizationId, UUID sourceId, Instant at, String errorCode, boolean credentialRejected) {
        sourceRepository.findByIdAndOrganizationId(sourceId, organizationId)
                .ifPresent(source -> source.recordSync(at, errorCode, credentialRejected));
    }

    private void save(IntegrationSource source, SourceType sourceType) {
        try {
            sourceRepository.saveAndFlush(source);
        } catch (DataIntegrityViolationException exception) {
            throw violates(exception, EXTERNAL_UNIQUE_CONSTRAINT)
                    ? new SourceAlreadyConnectedException(sourceType)
                    : exception;
        }
    }

    private void requireNotConnected(UUID organizationId, SourceType sourceType, String externalProjectKey) {
        if (sourceRepository.existsByOrganizationIdAndSourceTypeAndExternalProjectKey(
                organizationId,
                sourceType,
                externalProjectKey
        )) {
            throw new SourceAlreadyConnectedException(sourceType);
        }
    }

    private IntegrationSource find(UUID organizationId, UUID projectId, UUID sourceId) {
        requireProject(organizationId, projectId);
        return sourceRepository.findByIdAndOrganizationIdAndProjectId(sourceId, organizationId, projectId)
                .orElseThrow(SourceNotFoundException::new);
    }

    private void requireProject(UUID organizationId, UUID projectId) {
        projectRepository.findByIdAndOrganizationId(projectId, organizationId)
                .orElseThrow(ProjectNotFoundException::new);
    }

    static IntegrationSourceView view(IntegrationSource source) {
        return new IntegrationSourceView(
                source.getId(),
                source.getSourceType(),
                WEBHOOK_DELIVERY,
                source.getExternalProjectKey(),
                source.getRepositoryOwner(),
                source.getRepositoryName(),
                source.getApiBaseUrl(),
                source.getWebhookAuthMode(),
                source.getWebhookId(),
                webhookPath(source.getSourceType(), source.getWebhookId()),
                source.getCreatedAt(),
                source.getLastDeliveryAt(),
                source.hasAccessToken(),
                source.getTokenUpdatedAt(),
                source.getConnectionStatus(),
                source.getLastSyncAt(),
                source.getLastErrorCode()
        );
    }

    static String webhookPath(SourceType sourceType, UUID webhookId) {
        return switch (sourceType) {
            case GITHUB -> "/webhooks/github/" + webhookId;
            case GITLAB -> "/webhooks/gitlab/" + webhookId;
        };
    }

    /** The authenticated data of a source's webhook secret, which binds it to that source. */
    static byte[] secretAuthenticatedData(IntegrationSource source) {
        return switch (source.getSourceType()) {
            case GITHUB -> gitHubAuthenticatedData(
                    source.getOrganizationId(),
                    source.getProjectId(),
                    source.getId(),
                    source.getRepositoryOwner(),
                    source.getRepositoryName()
            );
            case GITLAB -> gitLabAuthenticatedData(
                    source.getOrganizationId(),
                    source.getProjectId(),
                    source.getId(),
                    source.getExternalProjectKey()
            );
        };
    }

    // Unchanged since sources were GitHub integrations, so existing ciphertexts still decrypt.
    static byte[] gitHubAuthenticatedData(
            UUID organizationId,
            UUID projectId,
            UUID sourceId,
            String owner,
            String repository
    ) {
        return String.join(
                        "\n",
                        organizationId.toString(),
                        projectId.toString(),
                        sourceId.toString(),
                        owner,
                        repository
                )
                .getBytes(StandardCharsets.UTF_8);
    }

    // The type line keeps a GitLab ciphertext from being read as a GitHub one.
    static byte[] gitLabAuthenticatedData(UUID organizationId, UUID projectId, UUID sourceId, String projectPath) {
        return String.join(
                        "\n",
                        organizationId.toString(),
                        projectId.toString(),
                        sourceId.toString(),
                        SourceType.GITLAB.name(),
                        projectPath
                )
                .getBytes(StandardCharsets.UTF_8);
    }

    // The purpose line keeps a token ciphertext from being accepted as a webhook secret.
    static byte[] tokenAuthenticatedData(IntegrationSource source) {
        String purpose = switch (source.getSourceType()) {
            case GITHUB -> GITHUB_TOKEN_PURPOSE;
            case GITLAB -> GITLAB_TOKEN_PURPOSE;
        };
        return String.join(
                        "\n",
                        new String(secretAuthenticatedData(source), StandardCharsets.UTF_8),
                        purpose
                )
                .getBytes(StandardCharsets.UTF_8);
    }

    private String generateWebhookSecret() {
        byte[] secret = new byte[WEBHOOK_SECRET_BYTES];
        secureRandom.nextBytes(secret);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    private static String canonicalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean violates(DataIntegrityViolationException exception, String constraint) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }

    private record Coordinates(
            SourceType sourceType,
            String externalProjectKey,
            String apiBaseUrl,
            String repositoryOwner,
            String repositoryName
    ) {
    }
}
