package com.hoangluongtran0309.releaseflow.release;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ReleaseChangeReviewRepository extends JpaRepository<ReleaseChangeReview, ReleaseChange.Key> {

    List<ReleaseChangeReview> findAllByReleaseIdAndOrganizationId(UUID releaseId, UUID organizationId);

    long countByReleaseIdAndOrganizationId(UUID releaseId, UUID organizationId);

    long deleteByReleaseIdAndOrganizationId(UUID releaseId, UUID organizationId);
}
