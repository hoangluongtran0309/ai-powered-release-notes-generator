package com.hoangluongtran0309.releaseflow.automation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Which addresses a Confluence action may be pointed at. */
class ConfluenceSiteTest {

    private final ConfluenceSite sites = new ConfluenceSite();

    @Test
    void acceptsACloudSiteAndReducesItToItsOrigin() {
        assertThat(sites.validated("https://acme.atlassian.net")).isEqualTo("https://acme.atlassian.net");
        assertThat(sites.validated("https://ACME.atlassian.net/")).isEqualTo("https://acme.atlassian.net");
        assertThat(sites.validated("  https://acme.atlassian.net///  ")).isEqualTo("https://acme.atlassian.net");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://acme.atlassian.net",
            "https://atlassian.net",
            "https://acme.atlassian.net.evil.test",
            "https://evil.test/acme.atlassian.net",
            "https://team.acme.atlassian.net",
            "https://acme.atlassian.net:8443",
            "https://acme.atlassian.net/wiki",
            "https://user:pass@acme.atlassian.net",
            "https://acme.atlassian.net?site=evil",
            "https://acme.atlassian.net#fragment",
            "https://169.254.169.254",
            "not a url"
    })
    void refusesAnythingElse(String value) {
        assertThatThrownBy(() -> sites.validated(value))
                .isInstanceOf(AutomationActionInvalidException.class)
                .extracting(exception -> ((AutomationActionInvalidException) exception).code())
                .isEqualTo("automation_confluence_site_invalid");
    }

    @Test
    void refusesAMissingSite() {
        assertThatThrownBy(() -> sites.validated(null))
                .isInstanceOf(AutomationActionInvalidException.class)
                .extracting(exception -> ((AutomationActionInvalidException) exception).code())
                .isEqualTo("automation_confluence_site_required");
    }
}
