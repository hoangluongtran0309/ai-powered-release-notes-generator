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
                    case PUBLIC_CHANGELOG -> "A public changelog action publishes inside ReleaseFlow itself.";
                    // These do take one, so nothing may report that theirs was refused.
                    case NOTION, CONFLUENCE, MICROSOFT_TEAMS, ZENDESK -> throw new IllegalStateException(
                            actionType + " takes a secret of its own.");
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

    static AutomationActionInvalidException notionParentRequired() {
        return new AutomationActionInvalidException(
                "automation_notion_parent_required",
                "A Notion action needs the page it files release notes under."
        );
    }

    static AutomationActionInvalidException notionParentInvalid() {
        return new AutomationActionInvalidException(
                "automation_notion_parent_invalid",
                "A Notion page id is 32 hexadecimal characters, with or without dashes."
        );
    }

    static AutomationActionInvalidException notionTokenRequired() {
        return new AutomationActionInvalidException(
                "automation_notion_token_required",
                "A Notion action needs its integration token."
        );
    }

    static AutomationActionInvalidException confluenceSiteRequired() {
        return new AutomationActionInvalidException(
                "automation_confluence_site_required",
                "A Confluence action needs its site address."
        );
    }

    static AutomationActionInvalidException confluenceSiteInvalid() {
        return new AutomationActionInvalidException(
                "automation_confluence_site_invalid",
                "A Confluence site is an HTTPS atlassian.net address such as "
                        + "https://example.atlassian.net, with nothing after the host."
        );
    }

    static AutomationActionInvalidException confluenceEmailRequired() {
        return new AutomationActionInvalidException(
                "automation_confluence_email_required",
                "A Confluence action needs the email address its API token belongs to."
        );
    }

    static AutomationActionInvalidException confluenceEmailInvalid() {
        return new AutomationActionInvalidException(
                "automation_confluence_email_invalid",
                "The Confluence account is not an email address."
        );
    }

    static AutomationActionInvalidException confluenceSpaceRequired() {
        return new AutomationActionInvalidException(
                "automation_confluence_space_required",
                "A Confluence action needs the numeric id of the space it writes to."
        );
    }

    static AutomationActionInvalidException confluenceSpaceInvalid() {
        return new AutomationActionInvalidException(
                "automation_confluence_space_invalid",
                "A Confluence space id is a number."
        );
    }

    static AutomationActionInvalidException confluenceParentInvalid() {
        return new AutomationActionInvalidException(
                "automation_confluence_parent_invalid",
                "A Confluence parent page id is a number."
        );
    }

    static AutomationActionInvalidException confluenceTokenRequired() {
        return new AutomationActionInvalidException(
                "automation_confluence_token_required",
                "A Confluence action needs its API token."
        );
    }

    static AutomationActionInvalidException teamsWebhookRequired() {
        return new AutomationActionInvalidException(
                "automation_teams_webhook_required",
                "A Microsoft Teams action needs its Workflows callback URL."
        );
    }

    static AutomationActionInvalidException teamsWebhookInvalid() {
        return new AutomationActionInvalidException(
                "automation_teams_webhook_invalid",
                "A Microsoft Teams callback must be the HTTPS Workflows URL the flow shows, on a "
                        + "powerplatform.com environment host and carrying its own signature."
        );
    }

    static AutomationActionInvalidException zendeskSubdomainRequired() {
        return new AutomationActionInvalidException(
                "automation_zendesk_subdomain_required",
                "A Zendesk action needs the subdomain your help centre answers on."
        );
    }

    static AutomationActionInvalidException zendeskSubdomainInvalid() {
        return new AutomationActionInvalidException(
                "automation_zendesk_subdomain_invalid",
                "A Zendesk subdomain is one label, such as acme in acme.zendesk.com."
        );
    }

    static AutomationActionInvalidException zendeskClientIdRequired() {
        return new AutomationActionInvalidException(
                "automation_zendesk_client_id_required",
                "A Zendesk action needs its OAuth client ID."
        );
    }

    static AutomationActionInvalidException zendeskClientSecretRequired() {
        return new AutomationActionInvalidException(
                "automation_zendesk_client_secret_required",
                "A Zendesk action needs its OAuth client secret."
        );
    }

    static AutomationActionInvalidException zendeskSectionRequired() {
        return new AutomationActionInvalidException(
                "automation_zendesk_section_required",
                "A Zendesk action needs the numeric id of the section it writes to."
        );
    }

    static AutomationActionInvalidException zendeskSectionInvalid() {
        return new AutomationActionInvalidException(
                "automation_zendesk_section_invalid",
                "A Zendesk section id is a positive number."
        );
    }

    static AutomationActionInvalidException zendeskUserSegmentInvalid() {
        return new AutomationActionInvalidException(
                "automation_zendesk_user_segment_invalid",
                "A Zendesk user segment id is a positive number."
        );
    }

    static AutomationActionInvalidException cronInvalid() {
        return new AutomationActionInvalidException(
                "automation_cron_invalid",
                "A schedule needs a six-field cron expression, such as 0 0 9 * * MON."
        );
    }

    static AutomationActionInvalidException cronTimeZoneInvalid() {
        return new AutomationActionInvalidException(
                "automation_cron_time_zone_invalid",
                "A schedule needs an IANA time zone, such as Europe/Berlin."
        );
    }

    static AutomationActionInvalidException cronHasNoFutureOccurrence() {
        return new AutomationActionInvalidException(
                "automation_cron_no_occurrence",
                "This schedule never comes round again."
        );
    }

    static AutomationActionInvalidException cronReleaseRequired() {
        return new AutomationActionInvalidException(
                "automation_cron_release_required",
                "A scheduled rule needs the published release it repeats."
        );
    }

    static AutomationActionInvalidException reminderDaysInvalid() {
        return new AutomationActionInvalidException(
                "automation_reminder_days_invalid",
                "A reminder is sent between 0 and 365 days before the planned release."
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
