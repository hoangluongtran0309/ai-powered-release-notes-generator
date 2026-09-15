package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.github.GitHubAccess;
import com.hoangluongtran0309.releaseflow.github.GitHubApiClient;
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
 * A Project's integration sources. Each GitHub repository gets its own webhook ID and
 * signing secret, revealed once, and an optional write-only access token. Secrets and
 * tokens are encrypted with authenticated data that binds them to their source.
 */
@Service
public class IntegrationSourceService {

    private static final int WEBHOOK_SECRET_BYTES = 32;
    private static final String EXTERNAL_UNIQUE_CONSTRAINT = "integration_sources_external_unique";
    private static final String TOKEN_PURPOSE = "github-access-token";
    private static final String WEBHOOK_DELIVERY = "WEBHOOK";

    private final ProjectRepository projectRepository;
    private final IntegrationSourceRepository sourceRepository;
    private final CredentialCipher credentialCipher;
    private final GitHubApiClient gitHubApiClient;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    IntegrationSourceService(
            ProjectRepository projectRepository,
            IntegrationSourceRepository sourceRepository,
            CredentialCipher credentialCipher,
            GitHubApiClient gitHubApiClient,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this(
                projectRepository,
                sourceRepository,
                credentialCipher,
                gitHubApiClient,
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
            PlatformTransactionManager transactionManager,
            Clock clock,
            SecureRandom secureRandom
    ) {
        this.projectRepository = projectRepository;
        this.sourceRepository = sourceRepository;
        this.credentialCipher = credentialCipher;
        this.gitHubApiClient = gitHubApiClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    /**
     * Connects a GitHub repository to a Project. A repository belongs to at most one
     * Project of the Organization; a Project may have several.
     */
    @Transactional
    public IntegrationSourceCreated create(UUID organizationId, UUID projectId, IntegrationSourceRequest request) {
        requireProject(organizationId, projectId);

        String owner = canonicalize(request.getOwner());
        String repository = canonicalize(request.getRepository());
        if (sourceRepository.existsByOrganizationIdAndSourceTypeAndExternalProjectKey(
                organizationId,
                SourceType.GITHUB,
                owner + "/" + repository
        )) {
            throw new GitHubRepositoryAlreadyConnectedException();
        }

        UUID sourceId = UUID.randomUUID();
        UUID webhookId = UUID.randomUUID();
        Instant createdAt = clock.instant();
        String webhookSecret = generateWebhookSecret();
        CredentialCipher.EncryptedSecret encryptedSecret = credentialCipher.encrypt(
                webhookSecret,
                additionalAuthenticatedData(organizationId, projectId, sourceId, owner, repository)
        );

        IntegrationSource source = new IntegrationSource(
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
        try {
            sourceRepository.saveAndFlush(source);
        } catch (DataIntegrityViolationException exception) {
            throw violates(exception, EXTERNAL_UNIQUE_CONSTRAINT) ? new GitHubRepositoryAlreadyConnectedException() : exception;
        }

        return new IntegrationSourceCreated(
                sourceId,
                projectId,
                SourceType.GITHUB,
                owner,
                repository,
                webhookId,
                webhookPath(webhookId),
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
     * Stores a new access token after GitHub confirms it can read the repository's pull
     * requests. The GitHub call runs between two short transactions, never inside one.
     */
    public void replaceToken(UUID organizationId, UUID projectId, UUID sourceId, GitHubTokenRequest request) {
        String token = request.getToken();
        IntegrationSource source = transactionTemplate.execute(status -> find(organizationId, projectId, sourceId));

        GitHubAccess access = gitHubApiClient.checkPullRequestAccess(
                source.getRepositoryOwner(),
                source.getRepositoryName(),
                token
        );
        if (access == GitHubAccess.REJECTED) {
            throw new GitHubTokenRejectedException();
        }
        if (access == GitHubAccess.UNAVAILABLE) {
            throw new GitHubUnavailableException();
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
                source.getWebhookId(),
                webhookPath(source.getWebhookId()),
                source.getCreatedAt(),
                source.getLastDeliveryAt(),
                source.hasAccessToken(),
                source.getTokenUpdatedAt(),
                source.getConnectionStatus(),
                source.getLastSyncAt(),
                source.getLastErrorCode()
        );
    }

    static String webhookPath(UUID webhookId) {
        return "/webhooks/github/" + webhookId;
    }

    // Unchanged since sources were GitHub integrations, so existing ciphertexts still decrypt.
    static byte[] additionalAuthenticatedData(
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

    // The purpose line keeps a token ciphertext from being accepted as a webhook secret.
    static byte[] tokenAuthenticatedData(IntegrationSource source) {
        return String.join(
                        "\n",
                        source.getOrganizationId().toString(),
                        source.getProjectId().toString(),
                        source.getId().toString(),
                        source.getRepositoryOwner(),
                        source.getRepositoryName(),
                        TOKEN_PURPOSE
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
}
