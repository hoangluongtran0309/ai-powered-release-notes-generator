package com.hoangluongtran0309.releaseflow.change;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
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

    List<Change> findAllByOrganizationIdAndProjectIdAndProcessingStatusOrderByMergedAtAscIdAsc(
            UUID organizationId,
            UUID projectId,
            ProcessingStatus processingStatus
    );

    List<Change> findAllByOrganizationIdAndProjectIdAndIdInOrderByMergedAtAscIdAsc(
            UUID organizationId,
            UUID projectId,
            Collection<UUID> ids
    );

    List<Change> findTop500ByOrganizationIdAndProjectIdAndProcessingStatusAndIdNotAndReceivedAtAfterOrderByReceivedAtDescIdDesc(
            UUID organizationId,
            UUID projectId,
            ProcessingStatus processingStatus,
            UUID id,
            Instant receivedAfter
    );

    @Query("""
            select change from Change change
            where change.organizationId = :organizationId
              and change.projectId = :projectId
              and (:category is null or change.category = :category)
              and (:needsReview is null or change.needsReview = :needsReview)
              and (:insufficientContext = false
                   or change.contextStatus = com.hoangluongtran0309.releaseflow.change.ContextStatus.INSUFFICIENT)
              and (:reviewed is null
                   or (:reviewed = true and change.reviewedAt is not null)
                   or (:reviewed = false and change.reviewedAt is null))
            order by change.mergedAt desc, change.id desc
            """)
    List<Change> findInbox(
            @Param("organizationId") UUID organizationId,
            @Param("projectId") UUID projectId,
            @Param("category") String category,
            @Param("needsReview") Boolean needsReview,
            @Param("reviewed") Boolean reviewed,
            @Param("insufficientContext") boolean insufficientContext
    );
}
