package com.hoangluongtran0309.releaseflow.configuration;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.LocaleResolver;

import java.time.Duration;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Optional;

/**
 * Picks the language a request is answered in, in this order: the {@code releaseflow_lang}
 * cookie, the signed-in account's saved choice, {@code Accept-Language}, then the first
 * configured UI language. Each step is narrowed to a language this deployment ships, so an
 * unsupported ask falls through to the next step rather than to a missing bundle.
 *
 * <p>The account's choice is read from the principal the session already holds, so
 * resolving a locale costs no query. A change writes the cookie as well, and the cookie
 * outranks the account, so the person sees their new language at once.
 *
 * <p>Public changelog pages resolve the same way. They reach only the first, third and
 * fourth step, which keeps anonymous reading anonymous: no session is created and no
 * account is read.
 */
public class UiLocaleResolver implements LocaleResolver {

    static final String COOKIE_NAME = "releaseflow_lang";
    static final Duration COOKIE_MAX_AGE = Duration.ofDays(365);

    private static final String RESOLVED_ATTRIBUTE = UiLocaleResolver.class.getName() + ".RESOLVED";

    private final UiLanguages languages;

    public UiLocaleResolver(UiLanguages languages) {
        this.languages = languages;
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        if (request.getAttribute(RESOLVED_ATTRIBUTE) instanceof Locale cached) {
            return cached;
        }
        Locale resolved = fromCookie(request)
                .or(this::fromAccount)
                .or(() -> fromAcceptLanguage(request))
                .orElseGet(languages::fallback);
        request.setAttribute(RESOLVED_ATTRIBUTE, resolved);
        return resolved;
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        if (response == null) {
            throw new IllegalStateException("A language can only be chosen while answering a request.");
        }
        Optional<Locale> chosen = languages.narrow(locale);
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, chosen.map(Locale::toLanguageTag).orElse(""))
                .path("/")
                // Nothing in the browser reads this cookie: every label is rendered on the
                // server, so it stays out of reach of scripts.
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .maxAge(chosen.isPresent() ? COOKIE_MAX_AGE : Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        request.setAttribute(RESOLVED_ATTRIBUTE, chosen.orElseGet(() -> resolveWithoutCookie(request)));
    }

    private Locale resolveWithoutCookie(HttpServletRequest request) {
        return fromAccount()
                .or(() -> fromAcceptLanguage(request))
                .orElseGet(languages::fallback);
    }

    private Optional<Locale> fromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                Optional<Locale> narrowed = languages.narrow(cookie.getValue());
                if (narrowed.isPresent()) {
                    return narrowed;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Locale> fromAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ReleaseFlowPrincipal principal)) {
            return Optional.empty();
        }
        return languages.narrow(principal.uiLocale());
    }

    private Optional<Locale> fromAcceptLanguage(HttpServletRequest request) {
        // Without the header a servlet container answers getLocales() with its own default,
        // which says nothing about the reader, so the header has to be there first.
        if (request.getHeader(HttpHeaders.ACCEPT_LANGUAGE) == null) {
            return Optional.empty();
        }
        Enumeration<Locale> requested = request.getLocales();
        while (requested.hasMoreElements()) {
            Optional<Locale> narrowed = languages.narrow(requested.nextElement());
            if (narrowed.isPresent()) {
                return narrowed;
            }
        }
        return Optional.empty();
    }
}
