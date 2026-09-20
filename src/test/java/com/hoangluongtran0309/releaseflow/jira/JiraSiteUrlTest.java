package com.hoangluongtran0309.releaseflow.jira;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JiraSiteUrlTest {

    private final JiraSiteUrl sites = new JiraSiteUrl();

    @Test
    void acceptsACloudSiteAndDropsTrailingSlashes() {
        assertThat(sites.validated("https://acme.atlassian.net")).isEqualTo("https://acme.atlassian.net");
        assertThat(sites.validated("  https://acme.atlassian.net/  ")).isEqualTo("https://acme.atlassian.net");
        // However many there are, they come off in one walk down the address rather than
        // a search that restarts at every slash.
        assertThat(sites.validated("https://acme.atlassian.net" + "/".repeat(200)))
                .isEqualTo("https://acme.atlassian.net");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://acme.atlassian.net",
            "https://acme.example.com",
            "https://acme.atlassian.net:8443",
            "https://user:secret@acme.atlassian.net",
            "https://acme.atlassian.net?token=1",
            "https://acme.atlassian.net#fragment",
            "acme.atlassian.net",
            "https://"
    })
    void refusesAnythingButAPlainCloudAddress(String value) {
        assertThatThrownBy(() -> sites.validated(value)).isInstanceOf(InvalidJiraSiteException.class);
    }

    @Test
    void refusesAMissingOrOverlongSite() {
        assertThatThrownBy(() -> sites.validated(null)).isInstanceOf(InvalidJiraSiteException.class);
        assertThatThrownBy(() -> sites.validated("  ")).isInstanceOf(InvalidJiraSiteException.class);
        assertThatThrownBy(() -> sites.validated("https://acme.atlassian.net/" + "a".repeat(255)))
                .isInstanceOf(InvalidJiraSiteException.class);
    }
}
