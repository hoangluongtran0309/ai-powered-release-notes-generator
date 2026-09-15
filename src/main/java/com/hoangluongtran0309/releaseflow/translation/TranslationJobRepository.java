package com.hoangluongtran0309.releaseflow.translation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TranslationJobRepository extends JpaRepository<TranslationJob, UUID> {

    Optional<TranslationJob> findByChangeIdAndTargetLanguageAndInputHash(UUID changeId, String targetLanguage, String inputHash);

    List<TranslationJob> findAllByOrganizationIdAndChangeIdInAndStatus(
            UUID organizationId,
            Collection<UUID> changeIds,
            TranslationJob.Status status
    );

    // SKIP LOCKED lets several workers claim different jobs without waiting on each other.
    @Query(value = """
            SELECT * FROM translation_jobs
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at, created_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<TranslationJob> lockNextDue(@Param("now") Instant now);

    // Translating again is harmless, so a claim left by a stopped worker is simply released.
    @Modifying
    @Query(value = """
            UPDATE translation_jobs SET status = 'PENDING', next_attempt_at = :now
            WHERE status = 'RUNNING' AND claimed_at < :staleBefore
            """, nativeQuery = true)
    int recoverStale(@Param("staleBefore") Instant staleBefore, @Param("now") Instant now);
}
