package com.hoangluongtran0309.releaseflow.jira;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * The Jira sites ReleaseFlow will talk to. A site is the only Jira address a request may
 * choose, so it must be a plain HTTPS Atlassian Cloud host: no credentials, query,
 * fragment, or port. Unlike GitLab there is no deployment allowlist, because Jira Cloud
 * is one known domain.
 */
@Component
public class JiraSiteUrl {

    private static final String CLOUD_DOMAIN = "atlassian.net";
    private static final int MAX_LENGTH = 255;

    /** The submitted site without its trailing slashes, or an exception saying why not. */
    public String validated(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_LENGTH) {
            throw new InvalidJiraSiteException(
                    "The Jira site URL is required and must not exceed " + MAX_LENGTH + " characters.");
        }
        final URI uri;
        try {
            uri = new URI(value.strip());
        } catch (URISyntaxException exception) {
            throw new InvalidJiraSiteException("The Jira site URL is not a valid URL.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme() == null ? "" : uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || uri.getPort() != -1) {
            throw new InvalidJiraSiteException("The Jira site URL must be an HTTPS address with no credentials, "
                    + "port, query, or fragment.");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!host.equals(CLOUD_DOMAIN) && !host.endsWith("." + CLOUD_DOMAIN)) {
            throw new InvalidJiraSiteException("The Jira site must be an " + CLOUD_DOMAIN + " address.");
        }
        return withoutTrailingSlashes(uri.toString());
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
