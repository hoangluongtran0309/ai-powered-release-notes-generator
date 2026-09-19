package com.hoangluongtran0309.releaseflow.automation;

/** A rule or one of its actions was described in a way automation cannot carry out. */
public class AutomationActionInvalidException extends RuntimeException {

    private final String code;

    private AutomationActionInvalidException(String code, String message) {
        super(message);
        this.code = code;
    }

    static AutomationActionInvalidException actionRequired() {
        return new AutomationActionInvalidException(
                "automation_action_required",
                "A rule needs at least one action."
        );
    }

    static AutomationActionInvalidException projectNotFound() {
        return new AutomationActionInvalidException(
                "automation_project_not_found",
                "That project does not belong to your organization."
        );
    }

    static AutomationActionInvalidException audienceNotFound() {
        return new AutomationActionInvalidException(
                "automation_audience_not_found",
                "That audience does not belong to your organization."
        );
    }

    static AutomationActionInvalidException languageNotConfigured(String language) {
        return new AutomationActionInvalidException(
                "automation_language_not_configured",
                "Release notes are not written in " + language + ". Add it to the release languages first."
        );
    }

    static AutomationActionInvalidException secretNotAllowed(ActionType actionType) {
        return new AutomationActionInvalidException(
                "automation_secret_not_allowed",
                switch (actionType) {
                    case GITHUB_RELEASE -> "A GitHub Release action uses the project's own source token.";
                    case EMAIL -> "An email action uses the deployment's SMTP credentials.";
                    case SLACK -> "A Slack action takes its webhook URL as its secret.";
                }
        );
    }

    static AutomationActionInvalidException slackWebhookRequired() {
        return new AutomationActionInvalidException(
                "automation_slack_webhook_required",
                "A Slack action needs its incoming webhook URL."
        );
    }

    static AutomationActionInvalidException slackWebhookInvalid() {
        return new AutomationActionInvalidException(
                "automation_slack_webhook_invalid",
                "A Slack webhook must be an HTTPS /services/ URL on hooks.slack.com or hooks.slack-gov.com."
        );
    }

    static AutomationActionInvalidException emailRecipientsRequired() {
        return new AutomationActionInvalidException(
                "automation_email_recipients_required",
                "An email action needs between 1 and " + EmailRecipients.MAX_RECIPIENTS + " recipients."
        );
    }

    static AutomationActionInvalidException emailRecipientInvalid(String recipient) {
        return new AutomationActionInvalidException(
                "automation_email_recipient_invalid",
                "This is not an email address: " + recipient
        );
    }

    static AutomationActionInvalidException requestIdRequired() {
        return new AutomationActionInvalidException(
                "automation_request_id_required",
                "Running a rule by hand needs a request ID, so a repeat does not deliver twice."
        );
    }

    public String code() {
        return code;
    }
}
