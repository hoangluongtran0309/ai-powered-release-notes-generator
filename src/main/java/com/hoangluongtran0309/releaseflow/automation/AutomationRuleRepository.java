package com.hoangluongtran0309.releaseflow.automation;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
