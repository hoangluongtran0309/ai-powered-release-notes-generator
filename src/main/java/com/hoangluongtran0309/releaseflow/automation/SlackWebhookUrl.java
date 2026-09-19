package com.hoangluongtran0309.releaseflow.automation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The addresses a Slack action may post to. A webhook URL is a secret an administrator
 * submits, and it is the only delivery address ReleaseFlow takes from a request, so it
 * must carry nothing but a scheme, a host, and a {@code /services/} path, and its origin
 * must be one the deployment allows. That allowlist is what keeps a stored URL from
 * sending a release note to an internal address.
 *
 * <p>The URL is checked when the action is written and again before every delivery. An
 * allowlist entry is {@code host} or {@code host:port}, which means HTTPS, or a full
 * origin; writing the scheme is the only way to accept a host reached without TLS.
 */
@Component
public class SlackWebhookUrl {

    static final String PATH_PREFIX = "/services/";
    private static final int MAX_LENGTH = 255;

    private final Set<String> allowedOrigins;

    SlackWebhookUrl(@Value("${releaseflow.automation.slack.allowed-hosts}") String allowedHosts) {
        this.allowedOrigins = Arrays.stream(allowedHosts.split(","))
                .map(String::strip)
                .filter(entry -> !entry.isBlank())
                .map(SlackWebhookUrl::allowedOrigin)
                .collect(Collectors.toUnmodifiableSet());
        if (this.allowedOrigins.isEmpty()) {
            throw new IllegalStateException("releaseflow.automation.slack.allowed-hosts must name at least one host.");
        }
    }

    URI validated(String value) {
        if (value == null || value.isBlank()) {
            throw AutomationActionInvalidException.slackWebhookRequired();
        }
        if (value.length() > MAX_LENGTH) {
            throw AutomationActionInvalidException.slackWebhookInvalid();
        }
        final URI uri;
        try {
            uri = new URI(value.strip());
        } catch (URISyntaxException exception) {
            throw AutomationActionInvalidException.slackWebhookInvalid();
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean shaped = ("https".equals(scheme) || "http".equals(scheme))
                && uri.getHost() != null
                && uri.getUserInfo() == null
                && uri.getQuery() == null
                && uri.getFragment() == null
                && uri.getPath() != null
                && uri.getPath().startsWith(PATH_PREFIX)
                && uri.getPath().length() > PATH_PREFIX.length();
        if (!shaped || !allowedOrigins.contains(origin(scheme, uri.getHost(), uri.getPort()))) {
            throw AutomationActionInvalidException.slackWebhookInvalid();
        }
        return uri;
    }

    private static String allowedOrigin(String entry) {
        String withScheme = entry.contains("://") ? entry : "https://" + entry;
        final URI uri;
        try {
            uri = new URI(withScheme);
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(
                    "releaseflow.automation.slack.allowed-hosts entry is not a host or origin: " + entry);
        }
        if (uri.getHost() == null || uri.getScheme() == null) {
            throw new IllegalStateException(
                    "releaseflow.automation.slack.allowed-hosts entry is not a host or origin: " + entry);
        }
        return origin(uri.getScheme().toLowerCase(Locale.ROOT), uri.getHost(), uri.getPort());
    }

    private static String origin(String scheme, String host, int port) {
        return scheme + "://" + host.toLowerCase(Locale.ROOT) + (port == -1 ? "" : ":" + port);
    }
}
