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
import java.util.Locale;
import java.util.UUID;

@Service
class GitHubIntegrationService {

    private static final int WEBHOOK_SECRET_BYTES = 32;
    private static final String PROJECT_UNIQUE_CONSTRAINT = "github_integrations_project_unique";
    private static final String REPOSITORY_UNIQUE_CONSTRAINT = "github_integrations_repository_unique";
    private static final String TOKEN_PURPOSE = "github-access-token";

    private final ProjectRepository projectRepository;
    private final GitHubIntegrationRepository integrationRepository;
    private final CredentialCipher credentialCipher;
    private final GitHubApiClient gitHubApiClient;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    GitHubIntegrationService(
            ProjectRepository projectRepository,
            GitHubIntegrationRepository integrationRepository,
            CredentialCipher credentialCipher,
            GitHubApiClient gitHubApiClient,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this(
                projectRepository,
                integrationRepository,
                credentialCipher,
                gitHubApiClient,
                transactionManager,
                clock,
                new SecureRandom()
        );
    }

    GitHubIntegrationService(
            ProjectRepository projectRepository,
            GitHubIntegrationRepository integrationRepository,
            CredentialCipher credentialCipher,
            GitHubApiClient gitHubApiClient,
            PlatformTransactionManager transactionManager,
            Clock clock,
            SecureRandom secureRandom
    ) {
        this.projectRepository = projectRepository;
        this.integrationRepository = integrationRepository;
        this.credentialCipher = credentialCipher;
        this.gitHubApiClient = gitHubApiClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    @Transactional
    GitHubIntegrationCreated configure(
            UUID organizationId,
            UUID projectId,
            GitHubIntegrationRequest request
    ) {
        projectRepository.findByIdAndOrganizationId(projectId, organizationId)
                .orElseThrow(ProjectNotFoundException::new);

        String owner = canonicalize(request.getOwner());
        String repository = canonicalize(request.getRepository());
        if (integrationRepository.existsByProjectIdAndOrganizationId(projectId, organizationId)) {
            throw new GitHubIntegrationAlreadyConfiguredException();
        }
        if (integrationRepository.existsByOrganizationIdAndRepositoryOwnerAndRepositoryName(
                organizationId,
                owner,
                repository
        )) {
            throw new GitHubRepositoryAlreadyConnectedException();
        }

        UUID integrationId = UUID.randomUUID();
        UUID webhookId = UUID.randomUUID();
        Instant createdAt = clock.instant();
        String webhookSecret = generateWebhookSecret();
        byte[] authenticatedData = additionalAuthenticatedData(
                organizationId,
                projectId,
                integrationId,
                owner,
                repository
        );
        CredentialCipher.EncryptedSecret encryptedSecret = credentialCipher.encrypt(
                webhookSecret,
                authenticatedData
        );

        GitHubIntegration integration = new GitHubIntegration(
                integrationId,
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
            integrationRepository.saveAndFlush(integration);
        } catch (DataIntegrityViolationException exception) {
            throw translateConstraintViolation(exception);
        }

        return new GitHubIntegrationCreated(
                integrationId,
                projectId,
                owner,
                repository,
                webhookId,
                webhookPath(webhookId),
                webhookSecret,
                createdAt
        );
    }

    /**
     * Stores a new access token after GitHub confirms it can read the repository's pull
     * requests. The GitHub call runs between two short transactions, never inside one.
     */
    void replaceToken(UUID organizationId, UUID projectId, GitHubTokenRequest request) {
        String token = request.getToken();
        GitHubIntegration integration = transactionTemplate.execute(status -> find(organizationId, projectId));

        GitHubAccess access = gitHubApiClient.checkPullRequestAccess(
                integration.getRepositoryOwner(),
                integration.getRepositoryName(),
                token
        );
        if (access == GitHubAccess.REJECTED) {
            throw new GitHubTokenRejectedException();
        }
        if (access == GitHubAccess.UNAVAILABLE) {
            throw new GitHubUnavailableException();
        }

        transactionTemplate.executeWithoutResult(status -> {
            GitHubIntegration current = find(organizationId, projectId);
            current.replaceToken(
                    credentialCipher.encrypt(token, tokenAuthenticatedData(current)),
                    clock.instant()
            );
        });
    }

    private GitHubIntegration find(UUID organizationId, UUID projectId) {
        projectRepository.findByIdAndOrganizationId(projectId, organizationId)
                .orElseThrow(ProjectNotFoundException::new);
        return integrationRepository.findByProjectIdAndOrganizationId(projectId, organizationId)
                .orElseThrow(GitHubIntegrationNotFoundException::new);
    }

    static String webhookPath(UUID webhookId) {
        return "/webhooks/github/" + webhookId;
    }

    static byte[] additionalAuthenticatedData(
            UUID organizationId,
            UUID projectId,
            UUID integrationId,
            String owner,
            String repository
    ) {
        return String.join(
                        "\n",
                        organizationId.toString(),
                        projectId.toString(),
                        integrationId.toString(),
                        owner,
                        repository
                )
                .getBytes(StandardCharsets.UTF_8);
    }

    // The purpose line keeps a token ciphertext from being accepted as a webhook secret.
    static byte[] tokenAuthenticatedData(GitHubIntegration integration) {
        return String.join(
                        "\n",
                        integration.getOrganizationId().toString(),
                        integration.getProjectId().toString(),
                        integration.getId().toString(),
                        integration.getRepositoryOwner(),
                        integration.getRepositoryName(),
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

    private static RuntimeException translateConstraintViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && message.contains(PROJECT_UNIQUE_CONSTRAINT)) {
                return new GitHubIntegrationAlreadyConfiguredException();
            }
            if (message != null && message.contains(REPOSITORY_UNIQUE_CONSTRAINT)) {
                return new GitHubRepositoryAlreadyConnectedException();
            }
            cause = cause.getCause();
        }
        return exception;
    }
}
