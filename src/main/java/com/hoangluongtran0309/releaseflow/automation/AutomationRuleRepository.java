package com.hoangluongtran0309.releaseflow.automation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AutomationRuleRepository extends JpaRepository<AutomationRule, UUID> {

    Optional<AutomationRule> findByIdAndOrganizationIdAndActiveTrue(UUID id, UUID organizationId);

    List<AutomationRule> findAllByOrganizationIdAndActiveTrueOrderByNameAsc(UUID organizationId);

    boolean existsByOrganizationIdAndNameIgnoreCaseAndActiveTrue(UUID organizationId, String name);

    boolean existsByOrganizationIdAndNameIgnoreCaseAndActiveTrueAndIdNot(UUID organizationId, String name, UUID id);

    List<AutomationRule> findAllByOrganizationIdAndTriggerTypeAndEnabledTrueAndActiveTrueOrderByCreatedAtAscIdAsc(
            UUID organizationId,
            TriggerType triggerType
    );

    /** Every Organization's reminder rules, because a reminder is due of its own accord. */
    List<AutomationRule> findAllByTriggerTypeAndEnabledTrueAndActiveTrueOrderByCreatedAtAscIdAsc(
            TriggerType triggerType
    );

    /** The Rule a caller names by its webhook path, whatever the Organization. */
    Optional<AutomationRule> findByWebhookId(UUID webhookId);

    /**
     * The next schedule that has come round and nobody else holds. Locking the row keeps
     * two workers from booking the same occurrence twice, and SKIP LOCKED lets the
     * second one move on to another Rule instead of waiting.
     */
    @Query(value = """
            SELECT * FROM automation_rules
            WHERE trigger_type = 'SCHEDULED_CRON'
              AND enabled = TRUE
              AND active = TRUE
              AND next_fire_at IS NOT NULL
              AND next_fire_at <= :now
            ORDER BY next_fire_at, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<AutomationRule> lockNextDueCron(@Param("now") Instant now);
}
