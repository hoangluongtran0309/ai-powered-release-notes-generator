package com.hoangluongtran0309.releaseflow.automation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AutomationActionRunRepository extends JpaRepository<AutomationActionRun, UUID> {

    List<AutomationActionRun> findAllByRunIdOrderByPositionAsc(UUID runId);

    List<AutomationActionRun> findAllByRunIdInOrderByRunIdAscPositionAsc(Collection<UUID> runIds);

    /**
     * The next Action nobody else holds, from a Run that is neither finished nor being
     * cancelled, and only once every Action before it has succeeded. Locking the Action
     * row alone keeps two workers off the same delivery without blocking the Run.
     */
    @Query(value = """
            SELECT action_run.* FROM automation_action_runs action_run
            JOIN automation_runs run ON run.id = action_run.run_id
            WHERE action_run.status = 'PENDING'
              AND run.status IN ('PENDING', 'RUNNING')
              AND run.cancellation_requested = FALSE
              AND NOT EXISTS (
                  SELECT 1 FROM automation_action_runs previous
                  WHERE previous.run_id = action_run.run_id
                    AND previous.position < action_run.position
                    AND previous.status <> 'SUCCEEDED'
              )
            ORDER BY run.created_at, action_run.position
            LIMIT 1
            FOR UPDATE OF action_run SKIP LOCKED
            """, nativeQuery = true)
    Optional<AutomationActionRun> lockNextPending();

    /**
     * Deliveries cannot be repeated safely, so an Action whose worker stopped is never
     * released back to PENDING: it becomes UNKNOWN and waits for a person.
     */
    @Query(value = """
            SELECT id FROM automation_action_runs
            WHERE status = 'RUNNING' AND claimed_at < :staleBefore
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<UUID> findStaleRunningIds(@Param("staleBefore") Instant staleBefore);
}
