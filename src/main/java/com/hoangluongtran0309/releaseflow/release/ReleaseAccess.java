package com.hoangluongtran0309.releaseflow.release;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The only way other capabilities read a release and the notes it froze, always
 * scoped by Organization. It reads; a release is changed through its own service.
 */
@Component
public class ReleaseAccess {

    private final ReleaseRepository releaseRepository;
    private final AudienceReleaseNoteRepository noteRepository;

    ReleaseAccess(ReleaseRepository releaseRepository, AudienceReleaseNoteRepository noteRepository) {
        this.releaseRepository = releaseRepository;
        this.noteRepository = noteRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ReleaseSnapshot> find(UUID organizationId, UUID releaseId) {
        if (releaseId == null) {
            return Optional.empty();
        }
        return releaseRepository.findById(releaseId)
                .filter(release -> release.getOrganizationId().equals(organizationId))
                .map(release -> new ReleaseSnapshot(
                        release.getId(),
                        release.getProjectId(),
                        release.getVersion(),
                        release.getStatus(),
                        noteRepository
                                .findAllByReleaseIdAndOrganizationIdOrderByAudienceNameAscAudienceCodeAscLanguageAsc(
                                        release.getId(), organizationId)
                                .stream()
                                .map(AudienceReleaseNote::view)
                                .toList()
                ));
    }

    /** The Organization's published releases, newest first, for choosing one to deliver. */
    @Transactional(readOnly = true)
    public List<PublishedRelease> published(UUID organizationId) {
        return releaseRepository
                .findAllByOrganizationIdAndStatusOrderByCreatedAtDescIdDesc(organizationId, ReleaseStatus.PUBLISHED)
                .stream()
                .map(release -> new PublishedRelease(
                        release.getId(), release.getProjectId(), release.getVersion(), release.getPublishedAt()))
                .toList();
    }

    /** One published release, named the way a person picks it. */
    public record PublishedRelease(UUID id, UUID projectId, String version, Instant publishedAt) {
    }

    /** A release and the notes it holds, at the moment it was read. */
    public record ReleaseSnapshot(
            UUID releaseId,
            UUID projectId,
            String version,
            ReleaseStatus status,
            List<AudienceNoteView> notes
    ) {

        public ReleaseSnapshot {
            notes = List.copyOf(notes);
        }
    }
}
