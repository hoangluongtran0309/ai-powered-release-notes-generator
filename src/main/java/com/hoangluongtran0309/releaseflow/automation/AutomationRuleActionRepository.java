package com.hoangluongtran0309.releaseflow.automation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface AutomationRuleActionRepository extends JpaRepository<AutomationRuleAction, UUID> {

    List<AutomationRuleAction> findAllByRuleIdOrderByPositionAsc(UUID ruleId);

    List<AutomationRuleAction> findAllByRuleIdInOrderByRuleIdAscPositionAsc(Collection<UUID> ruleIds);
}
