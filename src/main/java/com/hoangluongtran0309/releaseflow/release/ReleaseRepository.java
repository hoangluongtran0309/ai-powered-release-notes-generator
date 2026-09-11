package com.hoangluongtran0309.releaseflow.release;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ReleaseRepository extends JpaRepository<Release, UUID> {

    Optional<Release> findByIdAndOrganizationIdAndProjectId(UUID id, UUID organizationId, UUID projectId);

    boolean existsByOrganizationIdAndProjectIdAndStatus(UUID organizationId, UUID projectId, ReleaseStatus status);

    boolean existsByOrganizationIdAndProjectIdAndVersionIgnoreCase(UUID organizationId, UUID projectId, String version);

    boolean existsByOrganizationIdAndProjectIdAndVersionIgnoreCaseAndIdNot(
            UUID organizationId,
            UUID projectId,
            String version,
            UUID releaseId
    );

    List<Release> findAllByOrganizationIdAndProjectIdOrderByCreatedAtDescIdDesc(UUID organizationId, UUID projectId);
}
