package com.hoangluongtran0309.releaseflow.changelog;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PublicChangelogEntryRepository extends JpaRepository<PublicChangelogEntry, UUID> {

    List<PublicChangelogEntry> findAllByOrganizationIdOrderByPublishedAtDescIdDesc(
            UUID organizationId,
            Pageable pageable
    );

    Optional<PublicChangelogEntry> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** The entry a second delivery of the same note would be. */
    Optional<PublicChangelogEntry> findByOrganizationIdAndReleaseIdAndAudienceNameSnapshotAndLanguage(
            UUID organizationId,
            UUID releaseId,
            String audienceNameSnapshot,
            String language
    );

    Optional<PublicChangelogEntry> findByActionRunId(UUID actionRunId);
}
