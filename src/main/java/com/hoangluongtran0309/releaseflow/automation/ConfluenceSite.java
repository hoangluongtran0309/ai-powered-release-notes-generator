package com.hoangluongtran0309.releaseflow.automation;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * The Confluence sites a Confluence Action may write to. The site is the only delivery
 * address ReleaseFlow takes from a request, so it must be a bare Atlassian Cloud origin:
 * HTTPS, one label beneath {@code atlassian.net}, and nothing else — no credentials,
 * port, path, query, or fragment. Only Confluence Cloud is supported, so unlike GitLab
 * there is no deployment allowlist to consult: the domain is the allowlist.
 *
 * <p>The site is checked when the Action is written and again before every delivery, so
 * a row that changed in the database cannot send a release note anywhere else.
 */
@Component
class ConfluenceSite {

    static final String KEY = "siteUrl";
    private static final String CLOUD_DOMAIN = ".atlassian.net";
    private static final int MAX_LENGTH = 255;

    /** The submitted site as the canonical origin to call, or an exception saying why not. */
    String validated(String value) {
        if (value == null || value.isBlank()) {
            throw AutomationActionInvalidException.confluenceSiteRequired();
        }
        if (value.length() > MAX_LENGTH) {
            throw AutomationActionInvalidException.confluenceSiteInvalid();
        }
        final URI uri;
        try {
            uri = new URI(withoutTrailingSlashes(value.strip()));
        } catch (URISyntaxException exception) {
            throw AutomationActionInvalidException.confluenceSiteInvalid();
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean shaped = "https".equalsIgnoreCase(uri.getScheme() == null ? "" : uri.getScheme())
                && uri.getUserInfo() == null
                && uri.getQuery() == null
                && uri.getFragment() == null
                && uri.getPort() == -1
                && (uri.getPath() == null || uri.getPath().isEmpty())
                // One label beneath the domain: the domain itself is not a site.
                && host.endsWith(CLOUD_DOMAIN)
                && host.length() > CLOUD_DOMAIN.length()
                && host.indexOf('.') == host.length() - CLOUD_DOMAIN.length();
        if (!shaped) {
            throw AutomationActionInvalidException.confluenceSiteInvalid();
        }
        return "https://" + host;
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
