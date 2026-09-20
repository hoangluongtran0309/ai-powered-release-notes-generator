package com.hoangluongtran0309.releaseflow.automation;

/** Something automation was asked to do that its current state does not allow. */
public class AutomationConflictException extends RuntimeException {

    private final String code;

    private AutomationConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    static AutomationConflictException nameTaken() {
        return new AutomationConflictException(
                "automation_rule_name_taken",
                "A rule with this name already exists."
        );
    }

    static AutomationConflictException ruleArchived() {
        return new AutomationConflictException(
                "automation_rule_archived",
                "An archived rule cannot be changed or run."
        );
    }

    static AutomationConflictException gitHubSourceMissing() {
        return new AutomationConflictException(
                "automation_github_source_missing",
                "A GitHub Release action needs the project to have exactly one GitHub source."
        );
    }

    static AutomationConflictException emailNotConfigured() {
        return new AutomationConflictException(
                "automation_email_not_configured",
                "Configure SMTP and a sender address before enabling an email action."
        );
    }

    static AutomationConflictException ruleUnavailable() {
        return new AutomationConflictException(
                "automation_rule_unavailable",
                "The rule must be enabled, run by hand, and cover the release's project."
        );
    }

    static AutomationConflictException releaseNotPublished() {
        return new AutomationConflictException(
                "automation_release_not_published",
                "Automation delivers published releases only."
        );
    }

    static AutomationConflictException scheduledActionUnsupported() {
        return new AutomationConflictException(
                "automation_scheduled_action_unsupported",
                "A rule that fires on a schedule or before a planned release may only tell people: "
                        + "use a Slack or an email action."
        );
    }

    static AutomationConflictException webhookRuleRequired() {
        return new AutomationConflictException(
                "automation_webhook_rule_required",
                "Only a rule called by another system has a webhook secret."
        );
    }

    static AutomationConflictException runNotRetryable() {
        return new AutomationConflictException(
                "automation_run_not_retryable",
                "Only a failed run, or one whose outcome is unknown, can be run again."
        );
    }

    static AutomationConflictException unknownNeedsConfirmation() {
        return new AutomationConflictException(
                "automation_unknown_needs_confirmation",
                "This action may already have been delivered. Confirm the duplicate before running it again."
        );
    }

    public String code() {
        return code;
    }
}
