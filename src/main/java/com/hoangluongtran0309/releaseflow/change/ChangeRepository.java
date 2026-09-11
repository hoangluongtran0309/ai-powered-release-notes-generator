package com.hoangluongtran0309.releaseflow.change;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ChangeRepository extends JpaRepository<Change, UUID> {

    boolean existsByProjectIdAndOrganizationIdAndPullRequestNumber(
            UUID projectId,
            UUID organizationId,
            int pullRequestNumber
    );

    Optional<Change> findByIdAndOrganizationIdAndProjectId(UUID id, UUID organizationId, UUID projectId);

    @Query("""
            select change from Change change
            where change.organizationId = :organizationId
              and change.projectId = :projectId
              and (:category is null or change.category = :category)
              and (:needsReview is null or change.needsReview = :needsReview)
              and (:reviewed is null
                   or (:reviewed = true and change.reviewedAt is not null)
                   or (:reviewed = false and change.reviewedAt is null))
            order by change.mergedAt desc, change.id desc
            """)
    List<Change> findInbox(
            @Param("organizationId") UUID organizationId,
            @Param("projectId") UUID projectId,
            @Param("category") ChangeCategory category,
            @Param("needsReview") Boolean needsReview,
            @Param("reviewed") Boolean reviewed
    );
}
