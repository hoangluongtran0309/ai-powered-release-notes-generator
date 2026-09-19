package com.hoangluongtran0309.releaseflow.automation;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AutomationRunRepository extends JpaRepository<AutomationRun, UUID> {

    Optional<AutomationRun> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<AutomationRun> findAllByOrganizationIdOrderByCreatedAtDescIdDesc(UUID organizationId, Pageable pageable);

    long countByOrganizationId(UUID organizationId);

    boolean existsByRuleIdAndReleaseIdAndTriggerType(UUID ruleId, UUID releaseId, TriggerType triggerType);

    Optional<AutomationRun> findByRuleIdAndRequestId(UUID ruleId, UUID requestId);

    List<AutomationRun> findAllByRuleIdAndStatusIn(UUID ruleId, Collection<ExecutionStatus> statuses);
}
