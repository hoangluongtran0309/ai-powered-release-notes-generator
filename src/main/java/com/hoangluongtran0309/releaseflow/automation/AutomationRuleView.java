package com.hoangluongtran0309.releaseflow.automation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One rule and the ordered actions it carries out, with whatever its trigger needs to
 * be understood: the release a schedule repeats, the schedule itself, how long before a
 * planned release a reminder goes out, or the path another system calls.
 *
 * <p>{@code webhookSecret} is filled in exactly once, by the response that creates or
 * rotates it. Every other view of the same rule says only whether a secret is stored.
 */
public record AutomationRuleView(
        UUID id,
        String name,
        TriggerType triggerType,
        UUID projectId,
        String projectName,
        UUID triggerReleaseId,
        String cronExpression,
        String cronTimeZone,
        Instant nextFireAt,
        Integer reminderDaysBefore,
        String webhookPath,
        boolean webhookSecretConfigured,
        String webhookSecret,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt,
        List<AutomationActionView> actions
) {

    public AutomationRuleView {
        actions = List.copyOf(actions);
    }

    /** The same rule, this once carrying the secret a caller will never see again. */
    AutomationRuleView revealing(String rawSecret) {
        return new AutomationRuleView(
                id,
                name,
                triggerType,
                projectId,
                projectName,
                triggerReleaseId,
                cronExpression,
                cronTimeZone,
                nextFireAt,
                reminderDaysBefore,
                webhookPath,
                webhookSecretConfigured,
                rawSecret,
                enabled,
                createdAt,
                updatedAt,
                actions
        );
    }

    /** A rule without a project watches every project of the Organization. */
    public boolean organizationWide() {
        return projectId == null;
    }
}
