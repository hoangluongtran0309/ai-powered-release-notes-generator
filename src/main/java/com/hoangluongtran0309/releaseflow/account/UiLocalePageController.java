package com.hoangluongtran0309.releaseflow.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.LocaleResolver;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/** The language picker in the user menu, which every member uses for their own account. */
@Controller
public class UiLocalePageController {

    private final UiLocaleService uiLocaleService;
    private final LocaleResolver localeResolver;

    UiLocalePageController(UiLocaleService uiLocaleService, LocaleResolver localeResolver) {
        this.uiLocaleService = uiLocaleService;
        this.localeResolver = localeResolver;
    }

    @PostMapping("/settings/ui-locale")
    String change(
            @org.springframework.security.core.annotation.AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @RequestParam(name = "uiLocale", required = false) String uiLocale,
            @RequestHeader(name = "Referer", required = false) String referer,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        Optional<Locale> chosen = uiLocaleService.choose(principal.userId(), uiLocale);
        localeResolver.setLocale(request, response, chosen.orElse(null));
        return "redirect:" + back(referer);
    }

    /**
     * Back to the page the picker was used on. Only the path of the referring URL is kept,
     * so a header somebody else set cannot send the person to another site.
     */
    private static String back(String referer) {
        if (referer == null || referer.isBlank()) {
            return "/";
        }
        try {
            String path = URI.create(referer).getPath();
            return path == null || path.isBlank() || !path.startsWith("/") || path.startsWith("//") ? "/" : path;
        } catch (IllegalArgumentException exception) {
            return "/";
        }
    }
}
