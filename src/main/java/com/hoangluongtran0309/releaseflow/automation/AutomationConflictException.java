package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** Something automation was asked to do that its current state does not allow. */
public class AutomationConflictException extends LocalizedException {

    private final String code;

    private AutomationConflictException(String code, Object... arguments) {
        super("error." + code, arguments);
        this.code = code;
    }

    static AutomationConflictException nameTaken() {
        return new AutomationConflictException("automation_rule_name_taken");
    }

    static AutomationConflictException ruleArchived() {
        return new AutomationConflictException("automation_rule_archived");
    }

    static AutomationConflictException gitHubSourceMissing() {
        return new AutomationConflictException("automation_github_source_missing");
    }

    static AutomationConflictException emailNotConfigured() {
        return new AutomationConflictException("automation_email_not_configured");
    }

    static AutomationConflictException ruleUnavailable() {
        return new AutomationConflictException("automation_rule_unavailable");
    }

    static AutomationConflictException releaseNotPublished() {
        return new AutomationConflictException("automation_release_not_published");
    }

    static AutomationConflictException scheduledActionUnsupported() {
        return new AutomationConflictException("automation_scheduled_action_unsupported");
    }

    static AutomationConflictException webhookRuleRequired() {
        return new AutomationConflictException("automation_webhook_rule_required");
    }

    static AutomationConflictException runNotRetryable() {
        return new AutomationConflictException("automation_run_not_retryable");
    }

    static AutomationConflictException unknownNeedsConfirmation() {
        return new AutomationConflictException("automation_unknown_needs_confirmation");
    }

    public String code() {
        return code;
    }
}
