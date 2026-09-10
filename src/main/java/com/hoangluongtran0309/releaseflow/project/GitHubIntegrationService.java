package com.hoangluongtran0309.releaseflow.project;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final ProjectRepository projectRepository;
    private final GitHubIntegrationRepository integrationRepository;
    private final CredentialCipher credentialCipher;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    GitHubIntegrationService(
            ProjectRepository projectRepository,
            GitHubIntegrationRepository integrationRepository,
            CredentialCipher credentialCipher,
            Clock clock
    ) {
        this(projectRepository, integrationRepository, credentialCipher, clock, new SecureRandom());
    }

    GitHubIntegrationService(
            ProjectRepository projectRepository,
            GitHubIntegrationRepository integrationRepository,
            CredentialCipher credentialCipher,
            Clock clock,
            SecureRandom secureRandom
    ) {
        this.projectRepository = projectRepository;
        this.integrationRepository = integrationRepository;
        this.credentialCipher = credentialCipher;
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
