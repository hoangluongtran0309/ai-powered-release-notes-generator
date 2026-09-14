package com.hoangluongtran0309.releaseflow.project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * The only way other capabilities obtain a Project's repository coordinates and
 * decrypted access token, always scoped by Organization and Project.
 */
@Component
public class GitHubRepositoryAccess {

    private static final Logger log = LoggerFactory.getLogger(GitHubRepositoryAccess.class);

    private final GitHubIntegrationRepository integrationRepository;
    private final CredentialCipher credentialCipher;

    GitHubRepositoryAccess(GitHubIntegrationRepository integrationRepository, CredentialCipher credentialCipher) {
        this.integrationRepository = integrationRepository;
        this.credentialCipher = credentialCipher;
    }

    @Transactional(readOnly = true)
    public Optional<GitHubRepositoryCredentials> find(UUID organizationId, UUID projectId) {
        return integrationRepository.findByProjectIdAndOrganizationId(projectId, organizationId)
                .map(integration -> new GitHubRepositoryCredentials(
                        integration.getRepositoryOwner(),
                        integration.getRepositoryName(),
                        decryptToken(integration)
                ));
    }

    // A token that no longer decrypts is treated as missing, which forces review.
    private String decryptToken(GitHubIntegration integration) {
        if (!integration.hasAccessToken()) {
            return null;
        }
        try {
            return credentialCipher.decrypt(
                    integration.getToken(),
                    GitHubIntegrationService.tokenAuthenticatedData(integration)
            );
        } catch (IllegalStateException exception) {
            log.warn("Could not decrypt the access token of GitHub integration {}.", integration.getId());
            return null;
        }
    }
}
