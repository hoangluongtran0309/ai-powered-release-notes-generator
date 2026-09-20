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

    /**
     * The approved releases whose planned time falls in {@code (after, until]}, oldest
     * first, for a rule that reminds people before one goes out. A null project means
     * every project of the Organization. A release with no planned time is not upcoming.
     */
    @Transactional(readOnly = true)
    public List<UpcomingRelease> upcomingApproved(UUID organizationId, UUID projectId, Instant after, Instant until) {
        if (!until.isAfter(after)) {
            return List.of();
        }
        return releaseRepository
                .findAllByOrganizationIdAndStatusAndPlannedReleaseAtGreaterThanAndPlannedReleaseAtLessThanEqualOrderByPlannedReleaseAtAscIdAsc(
                        organizationId, ReleaseStatus.APPROVED, after, until)
                .stream()
                .filter(release -> projectId == null || projectId.equals(release.getProjectId()))
                .map(release -> new UpcomingRelease(
                        release.getId(), release.getProjectId(), release.getVersion(), release.getPlannedReleaseAt()))
                .toList();
    }

    /** One published release, named the way a person picks it. */
    public record PublishedRelease(UUID id, UUID projectId, String version, Instant publishedAt) {
    }

    /** An approved release that has not gone out yet, and the moment it is planned for. */
    public record UpcomingRelease(UUID id, UUID projectId, String version, Instant plannedReleaseAt) {
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
