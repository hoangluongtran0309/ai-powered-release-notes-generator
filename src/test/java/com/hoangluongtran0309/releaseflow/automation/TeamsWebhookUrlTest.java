package com.hoangluongtran0309.releaseflow.automation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Which addresses a Microsoft Teams action may post to. */
class TeamsWebhookUrlTest {

    private static final String HOST = "https://contoso.environment.api.powerplatform.com";
    private static final String PATH = "/powerautomate/automations/direct/workflows/2f1a6c"
            + "/triggers/manual/paths/invoke";
    private static final String QUERY = "?api-version=1&sig=uT8k_signature-value";

    private final TeamsWebhookUrl webhooks = new TeamsWebhookUrl();

    @Test
    void acceptsTheCallbackWorkflowsHandsOut() {
        assertThat(webhooks.validated(HOST + PATH + QUERY)).hasToString(HOST + PATH + QUERY);
        // The scale-unit form newer flows carry, and a trailing slash, are the same URL.
        assertThat(webhooks.validated(HOST
                + "/powerautomate/automations/direct/cu/20/workflows/2f1a6c/triggers/manual/paths/invoke/" + QUERY))
                .isNotNull();
        assertThat(webhooks.validated(HOST + ":443" + PATH + QUERY)).isNotNull();
        assertThat(webhooks.validated("  " + HOST + PATH + "?sig=a  ")).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Not HTTPS, not the environment domain, or not a tenant beneath it.
            "http://contoso.environment.api.powerplatform.com/powerautomate/automations/direct/workflows/a"
                    + "/triggers/manual/paths/invoke?sig=a",
            "https://environment.api.powerplatform.com/powerautomate/automations/direct/workflows/a"
                    + "/triggers/manual/paths/invoke?sig=a",
            "https://contoso.environment.api.powerplatform.com.evil.test/powerautomate/automations/direct/workflows/a"
                    + "/triggers/manual/paths/invoke?sig=a",
            // Credentials, a port nothing serves on, or a fragment.
            "https://user@contoso.environment.api.powerplatform.com/powerautomate/automations/direct/workflows/a"
                    + "/triggers/manual/paths/invoke?sig=a",
            "https://contoso.environment.api.powerplatform.com:8443/powerautomate/automations/direct/workflows/a"
                    + "/triggers/manual/paths/invoke?sig=a",
            "https://contoso.environment.api.powerplatform.com/powerautomate/automations/direct/workflows/a"
                    + "/triggers/manual/paths/invoke?sig=a#fragment",
            // Somewhere else on the host, or a path that is not a manual trigger.
            "https://contoso.environment.api.powerplatform.com/?sig=a",
            "https://contoso.environment.api.powerplatform.com/powerautomate/automations/direct/workflows/a"
                    + "/triggers/manual/paths/other?sig=a",
            "https://contoso.environment.api.powerplatform.com/powerautomate/automations/direct/workflows/"
                    + "/triggers/manual/paths/invoke?sig=a",
            "https://contoso.environment.api.powerplatform.com/powerautomate/automations/direct/cu/x/workflows/a"
                    + "/triggers/manual/paths/invoke?sig=a",
            // No signature at all, an empty one, or one that only looks like it.
            "https://contoso.environment.api.powerplatform.com/powerautomate/automations/direct/workflows/a"
                    + "/triggers/manual/paths/invoke",
            "https://contoso.environment.api.powerplatform.com/powerautomate/automations/direct/workflows/a"
                    + "/triggers/manual/paths/invoke?sig=",
            "https://contoso.environment.api.powerplatform.com/powerautomate/automations/direct/workflows/a"
                    + "/triggers/manual/paths/invoke?notsig=a",
            "not a url"
    })
    void refusesAnythingElse(String value) {
        assertThatThrownBy(() -> webhooks.validated(value))
                .isInstanceOf(AutomationActionInvalidException.class)
                .extracting(exception -> ((AutomationActionInvalidException) exception).code())
                .isEqualTo("automation_teams_webhook_invalid");
    }

    @Test
    void refusesAMissingCallback() {
        assertThatThrownBy(() -> webhooks.validated("  "))
                .isInstanceOf(AutomationActionInvalidException.class)
                .extracting(exception -> ((AutomationActionInvalidException) exception).code())
                .isEqualTo("automation_teams_webhook_required");
    }
}
