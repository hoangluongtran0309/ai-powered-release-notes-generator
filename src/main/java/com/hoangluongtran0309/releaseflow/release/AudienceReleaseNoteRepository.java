package com.hoangluongtran0309.releaseflow.release;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AudienceReleaseNoteRepository extends JpaRepository<AudienceReleaseNote, UUID> {

    List<AudienceReleaseNote> findAllByReleaseIdAndOrganizationIdOrderByAudienceNameAscAudienceCodeAsc(
            UUID releaseId,
            UUID organizationId
    );

    Optional<AudienceReleaseNote> findByIdAndReleaseIdAndOrganizationId(UUID id, UUID releaseId, UUID organizationId);

    long deleteByReleaseIdAndOrganizationId(UUID releaseId, UUID organizationId);
}
