package com.hoangluongtran0309.releaseflow.release;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ReleaseChangeRepository extends JpaRepository<ReleaseChange, ReleaseChange.Key> {

    List<ReleaseChange> findAllByReleaseIdAndOrganizationId(UUID releaseId, UUID organizationId);

    List<ReleaseChange> findAllByOrganizationIdAndProjectId(UUID organizationId, UUID projectId);

    // A change belongs to at most one release.
    Optional<ReleaseChange> findByChangeIdAndOrganizationId(UUID changeId, UUID organizationId);

    long countByReleaseIdAndOrganizationId(UUID releaseId, UUID organizationId);

    long deleteByReleaseIdAndChangeIdAndOrganizationId(UUID releaseId, UUID changeId, UUID organizationId);
}
