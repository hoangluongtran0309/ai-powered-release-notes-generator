package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.source.SourceCredentials;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The only way other capabilities obtain a source's coordinates and decrypted access
 * token, always scoped by Organization and Project.
 */
@Component
public class SourceAccess {

    private static final Logger log = LoggerFactory.getLogger(SourceAccess.class);

    private final IntegrationSourceRepository sourceRepository;
    private final CredentialCipher credentialCipher;

    SourceAccess(IntegrationSourceRepository sourceRepository, CredentialCipher credentialCipher) {
        this.sourceRepository = sourceRepository;
        this.credentialCipher = credentialCipher;
    }

    @Transactional(readOnly = true)
    public Optional<SourceCredentials> find(UUID organizationId, UUID projectId, UUID sourceId) {
        if (sourceId == null) {
            return Optional.empty();
        }
        return sourceRepository.findByIdAndOrganizationIdAndProjectId(sourceId, organizationId, projectId)
                .map(this::credentials);
    }

    /**
     * The Project's source of one type, for the one case where a change is enriched from a
     * source that is not its own. A Project may hold more than one; the oldest wins, so the
     * answer is at least stable.
     */
    @Transactional(readOnly = true)
    public Optional<SourceCredentials> findByType(UUID organizationId, UUID projectId, SourceType sourceType) {
        return sourceRepository
                .findAllByOrganizationIdAndProjectIdAndSourceTypeOrderByCreatedAtAscIdAsc(
                        organizationId, projectId, sourceType)
                .stream()
                .findFirst()
                .map(this::credentials);
    }

    /**
     * The Project's only source of one type. A Project with none, or with more than
     * one, has no single answer, so automation refuses rather than guessing which
     * repository a release belongs in.
     */
    @Transactional(readOnly = true)
    public Optional<SourceCredentials> findSole(UUID organizationId, UUID projectId, SourceType sourceType) {
        List<IntegrationSource> sources = sourceRepository
                .findAllByOrganizationIdAndProjectIdAndSourceTypeOrderByCreatedAtAscIdAsc(
                        organizationId, projectId, sourceType);
        return sources.size() == 1 ? Optional.of(credentials(sources.getFirst())) : Optional.empty();
    }

    /** The polled sources whose next poll is due, oldest cursor first. */
    @Transactional(readOnly = true)
    public List<PolledSource> findDuePolls(SourceType sourceType, Instant now) {
        return sourceRepository.findAllBySourceTypeAndNextPollAtLessThanEqual(sourceType, now).stream()
                .map(source -> new PolledSource(
                        source.getId(),
                        source.getOrganizationId(),
                        source.getProjectId(),
                        source.getPollCursorAt() == null ? source.getCreatedAt() : source.getPollCursorAt()
                ))
                .toList();
    }

    private SourceCredentials credentials(IntegrationSource source) {
        return new SourceCredentials(
                source.getSourceType(),
                source.getExternalProjectKey(),
                source.getExternalWorkspaceKey(),
                source.getApiBaseUrl(),
                source.getCredentialIdentity(),
                source.getRepositoryOwner(),
                source.getRepositoryName(),
                decryptToken(source)
        );
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
