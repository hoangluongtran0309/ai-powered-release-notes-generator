package com.hoangluongtran0309.releaseflow.change;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SourceSyncJobRepository extends JpaRepository<SourceSyncJob, UUID> {

    Optional<SourceSyncJob> findFirstBySourceIdAndOrganizationIdOrderByCreatedAtDescIdDesc(UUID sourceId, UUID organizationId);

    List<SourceSyncJob> findAllByOrganizationIdAndProjectIdOrderByCreatedAtDescIdDesc(UUID organizationId, UUID projectId);

    // SKIP LOCKED lets several workers claim different jobs without waiting on each other.
    @Query(value = """
            SELECT * FROM source_sync_jobs
            WHERE status IN ('PENDING', 'RETRY_SCHEDULED') AND next_attempt_at <= :now
            ORDER BY next_attempt_at, created_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<SourceSyncJob> lockNextDue(@Param("now") Instant now);

    // Reading a page again is harmless: the cursor was saved before the page that was lost.
    @Modifying
    @Query(value = """
            UPDATE source_sync_jobs SET status = 'RETRY_SCHEDULED', next_attempt_at = :now
            WHERE status = 'RUNNING' AND claimed_at < :staleBefore
            """, nativeQuery = true)
    int recoverStale(@Param("staleBefore") Instant staleBefore, @Param("now") Instant now);
}
