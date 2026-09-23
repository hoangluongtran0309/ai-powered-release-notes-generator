package com.hoangluongtran0309.releaseflow.gitlab;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitLabBaseUrlTest {

    private final GitLabBaseUrl defaults = new GitLabBaseUrl("gitlab.com");

    @Test
    void acceptsAnAllowedHostAndDropsTrailingSlashes() {
        assertThat(defaults.validated("https://gitlab.com")).isEqualTo("https://gitlab.com");
        assertThat(defaults.validated("https://gitlab.com//")).isEqualTo("https://gitlab.com");
        assertThat(defaults.validated("  https://GitLab.com/  ")).isEqualTo("https://GitLab.com");
        // However many there are, and whatever is in front of them, they come off in one
        // walk down the address rather than a search that restarts at every slash.
        assertThat(defaults.validated("https://gitlab.com/gitlab" + "/".repeat(200)))
                .isEqualTo("https://gitlab.com/gitlab");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://gitlab.com",
            "ftp://gitlab.com",
            "gitlab.com",
            "https://user:secret@gitlab.com",
            "https://gitlab.com?token=1",
            "https://gitlab.com#fragment",
            "https://"
    })
    void refusesAnythingButAPlainAllowedAddress(String value) {
        assertThatThrownBy(() -> defaults.validated(value))
                .isInstanceOfAny(InvalidGitLabBaseUrlException.class, GitLabHostNotAllowedException.class);
    }

    @Test
    void refusesAMissingOrOverlongUrl() {
        assertThatThrownBy(() -> defaults.validated(null)).isInstanceOf(InvalidGitLabBaseUrlException.class);
        assertThatThrownBy(() -> defaults.validated("  ")).isInstanceOf(InvalidGitLabBaseUrlException.class);
        assertThatThrownBy(() -> defaults.validated("https://gitlab.com/" + "a".repeat(255)))
                .isInstanceOf(InvalidGitLabBaseUrlException.class);
    }

    @Test
    void refusesAHostTheDeploymentDoesNotAllow() {
        // The origin refused is an argument of the sentence, not part of it.
        assertThatThrownBy(() -> defaults.validated("https://gitlab.internal"))
                .isInstanceOf(GitLabHostNotAllowedException.class)
                .hasMessage("error.gitlab_host_not_allowed")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(GitLabHostNotAllowedException.class))
                .extracting(GitLabHostNotAllowedException::arguments)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.array(Object[].class))
                .containsExactly("https://gitlab.internal");
        // A port is part of the origin, so the default entry does not cover one.
        assertThatThrownBy(() -> defaults.validated("https://gitlab.com:8443"))
                .isInstanceOf(GitLabHostNotAllowedException.class);
    }

    @Test
    void allowsAnEntryWithAPortOrAScheme() {
        GitLabBaseUrl allowlist = new GitLabBaseUrl("gitlab.com, gitlab.internal:8443 , http://gitlab.test");

        assertThatCode(() -> allowlist.validated("https://gitlab.internal:8443/gitlab")).doesNotThrowAnyException();
        // Writing the scheme is the only way a deployment accepts an instance without TLS.
        assertThatCode(() -> allowlist.validated("http://gitlab.test")).doesNotThrowAnyException();
        assertThatThrownBy(() -> allowlist.validated("https://gitlab.test"))
                .isInstanceOf(GitLabHostNotAllowedException.class);
    }

    @Test
    void refusesAnEmptyAllowlistAtStartup() {
        assertThatThrownBy(() -> new GitLabBaseUrl(" , "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("releaseflow.gitlab.allowed-hosts");
    }
}
