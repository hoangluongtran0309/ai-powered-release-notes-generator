package com.hoangluongtran0309.releaseflow.release;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ReleaseRepository extends JpaRepository<Release, UUID> {

    @Query("""
            select new com.hoangluongtran0309.releaseflow.release.ReleaseStatusCounts(
                       count(release),
                       coalesce(sum(case when release.status = com.hoangluongtran0309.releaseflow.release.ReleaseStatus.DRAFT then 1 else 0 end), 0),
                       coalesce(sum(case when release.status = com.hoangluongtran0309.releaseflow.release.ReleaseStatus.IN_REVIEW then 1 else 0 end), 0),
                       coalesce(sum(case when release.status = com.hoangluongtran0309.releaseflow.release.ReleaseStatus.APPROVED then 1 else 0 end), 0),
                       coalesce(sum(case when release.status = com.hoangluongtran0309.releaseflow.release.ReleaseStatus.PUBLISHED then 1 else 0 end), 0))
            from Release release
            where release.organizationId = :organizationId
              and release.projectId = :projectId
            """)
    ReleaseStatusCounts countByStatus(
            @Param("organizationId") UUID organizationId,
            @Param("projectId") UUID projectId
    );

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

    List<Release> findAllByOrganizationIdAndStatusOrderByCreatedAtDescIdDesc(UUID organizationId, ReleaseStatus status);

    List<Release> findAllByOrganizationIdAndStatusAndPlannedReleaseAtGreaterThanAndPlannedReleaseAtLessThanEqualOrderByPlannedReleaseAtAscIdAsc(
            UUID organizationId,
            ReleaseStatus status,
            Instant after,
            Instant until
    );
}
