package com.hoangluongtran0309.releaseflow.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;
import java.util.Optional;

@RestController
@RequestMapping("/api/me/ui-locale")
public class UiLocaleApiController {

    private final UiLocaleService uiLocaleService;
    private final LocaleResolver localeResolver;

    UiLocaleApiController(UiLocaleService uiLocaleService, LocaleResolver localeResolver) {
        this.uiLocaleService = uiLocaleService;
        this.localeResolver = localeResolver;
    }

    @GetMapping
    UiLocaleSettings get(@AuthenticationPrincipal ReleaseFlowPrincipal principal) {
        return uiLocaleService.settings(principal.userId());
    }

    /**
     * Saves the choice on the account and writes the language cookie, so the next request
     * is answered in it even though the session still holds the older principal.
     */
    @PutMapping
    UiLocaleSettings change(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @Valid @RequestBody UiLocaleRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        Optional<Locale> chosen = uiLocaleService.choose(principal.userId(), request.getUiLocale());
        localeResolver.setLocale(httpRequest, httpResponse, chosen.orElse(null));
        return uiLocaleService.settings(chosen.map(Locale::toLanguageTag).orElse(null));
    }
}
