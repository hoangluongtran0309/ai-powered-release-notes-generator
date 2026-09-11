package com.hoangluongtran0309.releaseflow.change;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface ChangeRepository extends JpaRepository<Change, UUID> {

    boolean existsByProjectIdAndOrganizationIdAndPullRequestNumber(
            UUID projectId,
            UUID organizationId,
            int pullRequestNumber
    );

    @Query("""
            select change from Change change
            where change.organizationId = :organizationId
              and change.projectId = :projectId
              and (:category is null or change.category = :category)
              and (:needsReview is null or change.needsReview = :needsReview)
            order by change.mergedAt desc, change.id desc
            """)
    List<Change> findInbox(
            @Param("organizationId") UUID organizationId,
            @Param("projectId") UUID projectId,
            @Param("category") ChangeCategory category,
            @Param("needsReview") Boolean needsReview
    );
}
