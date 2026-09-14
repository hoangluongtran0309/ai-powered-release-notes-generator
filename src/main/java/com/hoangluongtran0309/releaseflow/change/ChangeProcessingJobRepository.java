package com.hoangluongtran0309.releaseflow.change;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface ChangeProcessingJobRepository extends JpaRepository<ChangeProcessingJob, UUID> {

    // SKIP LOCKED lets several workers claim different jobs without waiting on each other.
    @Query(value = """
            SELECT * FROM change_processing_jobs
            WHERE status IN ('PENDING', 'FALLBACK_REQUIRED') AND next_attempt_at <= :now
            ORDER BY next_attempt_at, created_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<ChangeProcessingJob> lockNextDue(@Param("now") Instant now);

    // A worker that died mid-call leaves its claim behind. Collecting files is safe to
    // repeat, but an AI call is not: a stale CLASSIFYING job is completed without it.
    @Modifying
    @Query(value = """
            UPDATE change_processing_jobs
            SET status = CASE status WHEN 'CLASSIFYING' THEN 'FALLBACK_REQUIRED' ELSE 'PENDING' END,
                next_attempt_at = :now
            WHERE status IN ('ENRICHING', 'CLASSIFYING') AND claimed_at < :staleBefore
            """, nativeQuery = true)
    int recoverStale(@Param("staleBefore") Instant staleBefore, @Param("now") Instant now);

    Optional<ChangeProcessingJob> findByChangeId(UUID changeId);
}
