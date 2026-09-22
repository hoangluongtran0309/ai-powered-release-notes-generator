package com.hoangluongtran0309.releaseflow.overview;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Remembers which Project somebody was last looking at, so a page reached without
 * {@code ?project=} opens on it rather than on whichever Project happens to be first.
 *
 * <p>A cookie rather than browser storage, because the server renders the page: with
 * {@code localStorage} the first paint would show the wrong Project and a script would
 * have to correct it. The value carries no authority — the Project is still looked up
 * against the signed-in principal's Organization, and one that does not belong there is
 * simply not among the Projects offered.
 */
@Component
public class SelectedProject {

    static final String COOKIE_NAME = "releaseflow_project";
    static final Duration COOKIE_MAX_AGE = Duration.ofDays(365);

    /** The Project this browser was last shown, if it still names one. */
    public Optional<UUID> remembered(HttpServletRequest request) {
        return fromCookie(request);
    }

    /**
     * Records the Project a page settled on. Writing it on every page keeps the memory
     * true when somebody follows a link that names a different one.
     */
    public void remember(HttpServletRequest request, HttpServletResponse response, UUID projectId) {
        if (projectId == null || projectId.equals(fromCookie(request).orElse(null))) {
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE_NAME, projectId.toString())
                .path("/")
                // Nothing in the browser reads it; the server decides which Project to show.
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .maxAge(COOKIE_MAX_AGE)
                .build()
                .toString());
    }

    private static Optional<UUID> fromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                try {
                    return Optional.of(UUID.fromString(cookie.getValue()));
                } catch (IllegalArgumentException exception) {
                    // Somebody else's cookie, or a stale one: the page falls back to the
                    // first Project rather than to an error.
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }
}
