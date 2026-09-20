package com.hoangluongtran0309.releaseflow.changelog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Where a public changelog lives, as the deployment says it does. Every absolute link
 * ReleaseFlow writes — the canonical URL, an RSS item, the address an Action reports
 * back — is built from configuration and never from a request's {@code Host} or
 * forwarded headers, because a caller must not be able to decide what ReleaseFlow
 * publishes about itself.
 *
 * <p>With a base domain configured an Organization is served at
 * {@code https://{slug}.{domain}/}; without one, at {@code {base url}/changelog/{slug}}.
 * A deployment that wants the first has to provision wildcard DNS and TLS itself.
 */
@Component
public class PublicChangelogUrls {

    private static final Pattern DOMAIN = Pattern.compile("[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?");
    private static final Pattern LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

    private final String baseUrl;
    private final String baseDomain;

    PublicChangelogUrls(
            @Value("${releaseflow.public.base-url}") String baseUrl,
            @Value("${releaseflow.public.changelog-base-domain}") String baseDomain
    ) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.baseDomain = normalizedDomain(baseDomain);
    }

    /** The wildcard domain public changelogs answer under, or blank when there is none. */
    public String baseDomain() {
        return baseDomain;
    }

    /** Whether this slug can be served as a subdomain of the configured base domain. */
    public boolean servesSubdomain(String slug) {
        return !baseDomain.isBlank() && slug != null && LABEL.matcher(slug).matches();
    }

    public String rootUrl(String slug) {
        return servesSubdomain(slug)
                ? "https://" + slug + "." + baseDomain + "/"
                : baseUrl + "/changelog/" + slug;
    }

    public String entryUrl(String slug, UUID entryId) {
        return rootUrl(slug) + (servesSubdomain(slug) ? "" : "/") + "releases/" + entryId;
    }

    public String feedUrl(String slug) {
        return rootUrl(slug) + (servesSubdomain(slug) ? "" : "/") + "rss.xml";
    }

    // Checked once, at startup: a deployment learns of a typo before anybody follows a link.
    private static String normalizeBaseUrl(String value) {
        String candidate = value == null ? "" : value.strip();
        final URI uri;
        try {
            uri = URI.create(candidate);
        } catch (IllegalArgumentException notAUrl) {
            throw invalidBaseUrl(candidate);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean usable = (scheme.equals("http") || scheme.equals("https"))
                && uri.getHost() != null
                && uri.getUserInfo() == null
                && uri.getQuery() == null
                && uri.getFragment() == null
                && (uri.getPath() == null || uri.getPath().isBlank() || uri.getPath().equals("/"));
        if (!usable) {
            throw invalidBaseUrl(candidate);
        }
        String normalized = scheme + "://" + uri.getRawAuthority();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    /**
     * The wildcard domain as configuration means it, or blank when there is none. It is
     * static so the routing filter can read the same setting the same way without
     * depending on anything else.
     */
    static String normalizedDomain(String value) {
        String domain = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        while (domain.endsWith(".")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        if (domain.isBlank()) {
            return "";
        }
        if (!DOMAIN.matcher(domain).matches() || domain.contains("..")) {
            throw new IllegalStateException(
                    "releaseflow.public.changelog-base-domain must be a DNS name, or empty to serve "
                            + "public changelogs by path only."
            );
        }
        return domain;
    }

    private static IllegalStateException invalidBaseUrl(String value) {
        return new IllegalStateException(
                "releaseflow.public.base-url must be an absolute http or https URL without a path, "
                        + "such as https://releaseflow.example.com, but was \"" + value + "\"."
        );
    }
}
