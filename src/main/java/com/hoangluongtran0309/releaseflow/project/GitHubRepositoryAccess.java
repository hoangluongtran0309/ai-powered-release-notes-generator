package com.hoangluongtran0309.releaseflow.project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * The only way other capabilities obtain a source's repository coordinates and
 * decrypted access token, always scoped by Organization, Project, and source.
 */
@Component
public class GitHubRepositoryAccess {

    private static final Logger log = LoggerFactory.getLogger(GitHubRepositoryAccess.class);

    private final IntegrationSourceRepository sourceRepository;
    private final CredentialCipher credentialCipher;

    GitHubRepositoryAccess(IntegrationSourceRepository sourceRepository, CredentialCipher credentialCipher) {
        this.sourceRepository = sourceRepository;
        this.credentialCipher = credentialCipher;
    }

    @Transactional(readOnly = true)
    public Optional<GitHubRepositoryCredentials> find(UUID organizationId, UUID projectId, UUID sourceId) {
        if (sourceId == null) {
            return Optional.empty();
        }
        return sourceRepository.findByIdAndOrganizationIdAndProjectId(sourceId, organizationId, projectId)
                .map(source -> new GitHubRepositoryCredentials(
                        source.getRepositoryOwner(),
                        source.getRepositoryName(),
                        decryptToken(source)
                ));
    }

    // A token that no longer decrypts is treated as missing, which forces review.
    private String decryptToken(IntegrationSource source) {
        if (!source.hasAccessToken()) {
            return null;
        }
        try {
            return credentialCipher.decrypt(
                    source.getToken(),
                    IntegrationSourceService.tokenAuthenticatedData(source)
            );
        } catch (IllegalStateException exception) {
            log.warn("Could not decrypt the access token of source {}.", source.getId());
            return null;
        }
    }
}
