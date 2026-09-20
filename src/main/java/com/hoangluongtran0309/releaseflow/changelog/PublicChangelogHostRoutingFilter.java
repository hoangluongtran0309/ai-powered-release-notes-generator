package com.hoangluongtran0309.releaseflow.changelog;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Lets a deployment serve each Organization's changelog at
 * {@code {slug}.{base domain}} by rewriting exactly three paths onto the ones the
 * controller already answers. It runs before Spring Security, so the rest of the
 * application only ever sees the rewritten path.
 *
 * <p>It rewrites nothing unless a base domain is configured, the host is exactly one
 * label beneath it, the method is GET, and the path is one of the three. Everything
 * else — including a request for the signed-in application on that host — passes
 * through untouched. The slug still names the Organization; the host only says which
 * slug, and is never trusted for anything else.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class PublicChangelogHostRoutingFilter extends OncePerRequestFilter {

    private static final Pattern ENTRY = Pattern.compile("/releases/([0-9a-fA-F-]{36})");
    private static final Pattern LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

    private final String baseDomain;

    // Reads the setting rather than the URL factory, so nothing but a property has to
    // exist for this filter to be constructed.
    PublicChangelogHostRoutingFilter(@Value("${releaseflow.public.changelog-base-domain}") String baseDomain) {
        this.baseDomain = PublicChangelogUrls.normalizedDomain(baseDomain);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        String slug = slug(request.getServerName());
        String rewritten = slug == null || !HttpMethod.GET.matches(request.getMethod())
                ? null
                : rewrite(slug, path(request));
        if (rewritten == null) {
            chain.doFilter(request, response);
            return;
        }
        String uri = request.getContextPath() + rewritten;
        chain.doFilter(new HttpServletRequestWrapper(request) {

            @Override
            public String getRequestURI() {
                return uri;
            }

            @Override
            public String getServletPath() {
                return rewritten;
            }
        }, response);
    }

    private String slug(String serverName) {
        if (baseDomain.isBlank() || serverName == null) {
            return null;
        }
        String host = serverName.toLowerCase(Locale.ROOT);
        String suffix = "." + baseDomain;
        if (!host.endsWith(suffix)) {
            return null;
        }
        String label = host.substring(0, host.length() - suffix.length());
        // Exactly one label: a.b.example.com is not an Organization of example.com.
        return LABEL.matcher(label).matches() ? label : null;
    }

    private static String path(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String uri = request.getRequestURI();
        return contextPath == null || contextPath.isBlank() ? uri : uri.substring(contextPath.length());
    }

    private static String rewrite(String slug, String path) {
        if (path.equals("/") || path.isEmpty()) {
            return "/changelog/" + slug;
        }
        if (path.equals("/rss.xml")) {
            return "/changelog/" + slug + "/rss.xml";
        }
        var entry = ENTRY.matcher(path);
        return entry.matches() ? "/changelog/" + slug + "/releases/" + entry.group(1) : null;
    }
}
