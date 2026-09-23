package com.hoangluongtran0309.releaseflow.changelog;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicChangelogUrlsTest {

    private static final UUID ENTRY = UUID.fromString("0e1d4f0a-1d5a-4a5f-8f0c-2b0f5f4a9c11");

    @Test
    void buildsPathsWhenNoWildcardDomainIsConfigured() {
        PublicChangelogUrls urls = new PublicChangelogUrls("https://releaseflow.example.com", "");

        assertThat(urls.baseDomain()).isEmpty();
        assertThat(urls.servesSubdomain("acme")).isFalse();
        assertThat(urls.rootUrl("acme")).isEqualTo("https://releaseflow.example.com/changelog/acme");
        assertThat(urls.entryUrl("acme", ENTRY))
                .isEqualTo("https://releaseflow.example.com/changelog/acme/releases/" + ENTRY);
        assertThat(urls.feedUrl("acme")).isEqualTo("https://releaseflow.example.com/changelog/acme/rss.xml");
    }

    @Test
    void buildsSubdomainsWhenOneIsConfigured() {
        PublicChangelogUrls urls = new PublicChangelogUrls("http://localhost:8080", "Changelog.Example.COM.");

        assertThat(urls.baseDomain()).isEqualTo("changelog.example.com");
        assertThat(urls.servesSubdomain("acme")).isTrue();
        assertThat(urls.servesSubdomain("acme.tools")).isFalse();
        assertThat(urls.rootUrl("acme")).isEqualTo("https://acme.changelog.example.com/");
        assertThat(urls.entryUrl("acme", ENTRY)).isEqualTo("https://acme.changelog.example.com/releases/" + ENTRY);
        assertThat(urls.feedUrl("acme")).isEqualTo("https://acme.changelog.example.com/rss.xml");
    }

    @Test
    void refusesConfigurationNobodyCouldFollowALinkTo() {
        assertThatThrownBy(() -> new PublicChangelogUrls("releaseflow.example.com", ""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PublicChangelogUrls("https://example.com/changelog", ""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PublicChangelogUrls("https://user:pass@example.com", ""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PublicChangelogUrls("", ""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PublicChangelogUrls("https://example.com", "not a domain"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void keepsTheBaseUrlTidyWhateverItWasWritten() {
        assertThat(new PublicChangelogUrls("http://localhost:8080/", "").rootUrl("acme"))
                .isEqualTo("http://localhost:8080/changelog/acme");
        assertThat(new PublicChangelogUrls("HTTPS://Example.com", "").rootUrl("acme"))
                .isEqualTo("https://Example.com/changelog/acme");
    }
}
