package com.hoangluongtran0309.releaseflow.gitlab;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The GitLab instances a deployment may talk to. A base URL an administrator submits is
 * the only provider address ReleaseFlow takes from a request, so it must carry nothing
 * but a scheme, a host, and a path, and its origin must be one the deployment allows.
 * That allowlist is the boundary which keeps a source from pointing ReleaseFlow at an
 * internal address.
 *
 * <p>An allowlist entry is {@code host} or {@code host:port}, which means HTTPS, or a
 * full origin such as {@code http://gitlab.internal:8080}. Writing the scheme is the only
 * way a deployment can accept a GitLab instance reached without TLS.
 */
@Component
public class GitLabBaseUrl {

    private static final int MAX_LENGTH = 255;

    private final Set<String> allowedOrigins;

    GitLabBaseUrl(@Value("${releaseflow.gitlab.allowed-hosts}") String allowedHosts) {
        this.allowedOrigins = Arrays.stream(allowedHosts.split(","))
                .map(String::strip)
                .filter(entry -> !entry.isBlank())
                .map(GitLabBaseUrl::allowedOrigin)
                .collect(Collectors.toUnmodifiableSet());
        if (this.allowedOrigins.isEmpty()) {
            throw new IllegalStateException("releaseflow.gitlab.allowed-hosts must name at least one host.");
        }
    }

    /** The submitted base URL without its trailing slashes, or an exception saying why not. */
    public String validated(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_LENGTH) {
            throw new InvalidGitLabBaseUrlException(
                    "error.gitlab_base_url_invalid.length", MAX_LENGTH);
        }
        final URI uri;
        try {
            uri = new URI(value.strip());
        } catch (URISyntaxException exception) {
            throw new InvalidGitLabBaseUrlException("error.gitlab_base_url_invalid.unreadable");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!("https".equals(scheme) || "http".equals(scheme))
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new InvalidGitLabBaseUrlException("error.gitlab_base_url_invalid.shape");
        }
        String origin = origin(scheme, uri.getHost(), uri.getPort());
        if (!allowedOrigins.contains(origin)) {
            throw new GitLabHostNotAllowedException("error.gitlab_host_not_allowed", origin);
        }
        return withoutTrailingSlashes(uri.toString());
    }

    private static String allowedOrigin(String entry) {
        String withScheme = entry.contains("://") ? entry : "https://" + entry;
        final URI uri;
        try {
            uri = new URI(withScheme);
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("releaseflow.gitlab.allowed-hosts entry is not a host or origin: " + entry);
        }
        if (uri.getHost() == null || uri.getScheme() == null) {
            throw new IllegalStateException("releaseflow.gitlab.allowed-hosts entry is not a host or origin: " + entry);
        }
        return origin(uri.getScheme().toLowerCase(Locale.ROOT), uri.getHost(), uri.getPort());
    }

    private static String origin(String scheme, String host, int port) {
        return scheme + "://" + host.toLowerCase(Locale.ROOT) + (port == -1 ? "" : ":" + port);
    }

    // Walked rather than matched: a regular expression anchored at the end of a string
    // of slashes is retried from every position, which costs time proportional to the
    // square of the address's length.
    private static String withoutTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
