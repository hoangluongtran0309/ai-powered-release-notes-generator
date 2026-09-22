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

    boolean existsBySourceIdAndExternalId(UUID sourceId, String externalId);

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
            select new com.hoangluongtran0309.releaseflow.change.ChangeCounts(
                       count(change),
                       coalesce(sum(case when change.needsReview = true then 1 else 0 end), 0),
                       coalesce(sum(case when change.breaking = true then 1 else 0 end), 0))
            from Change change
            where change.organizationId = :organizationId
              and change.projectId = :projectId
            """)
    ChangeCounts countInbox(
            @Param("organizationId") UUID organizationId,
            @Param("projectId") UUID projectId
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
