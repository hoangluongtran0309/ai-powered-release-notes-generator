package com.hoangluongtran0309.releaseflow.automation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One rule and the ordered actions it carries out. */
public record AutomationRuleView(
        UUID id,
        String name,
        TriggerType triggerType,
        UUID projectId,
        String projectName,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt,
        List<AutomationActionView> actions
) {

    public AutomationRuleView {
        actions = List.copyOf(actions);
    }

    /** A rule without a project watches every project of the Organization. */
    public boolean organizationWide() {
        return projectId == null;
    }
}
