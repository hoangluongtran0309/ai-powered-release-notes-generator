package com.hoangluongtran0309.releaseflow.automation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface AutomationPublishJobRepository extends JpaRepository<AutomationPublishJob, UUID> {

    boolean existsByReleaseId(UUID releaseId);

    // SKIP LOCKED lets several workers drain the outbox without waiting on each other.
    @Query(value = """
            SELECT * FROM automation_publish_jobs
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at, created_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<AutomationPublishJob> lockNextDue(@Param("now") Instant now);

    // Creating Runs is idempotent per Rule and Release, so a lost claim is simply released.
    @Modifying
    @Query(value = """
            UPDATE automation_publish_jobs SET status = 'PENDING', next_attempt_at = :now
            WHERE status = 'RUNNING' AND claimed_at < :staleBefore
            """, nativeQuery = true)
    int recoverStale(@Param("staleBefore") Instant staleBefore, @Param("now") Instant now);
}
