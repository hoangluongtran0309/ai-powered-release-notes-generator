package com.hoangluongtran0309.releaseflow.configuration;

import com.hoangluongtran0309.releaseflow.account.AppUserRole;
import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.account.UiLocaleService;
import com.hoangluongtran0309.releaseflow.account.UiLocaleSettings;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;
import java.util.Locale;

@ControllerAdvice(annotations = Controller.class)
class PageModelAdvice {

    private final UiLocaleService uiLocaleService;

    PageModelAdvice(UiLocaleService uiLocaleService) {
        this.uiLocaleService = uiLocaleService;
    }

    @ModelAttribute("viewer")
    Viewer viewer(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ReleaseFlowPrincipal principal)) {
            return null;
        }
        String displayName = principal.displayName();
        String role = principal.role().name();
        return new Viewer(
                displayName,
                principal.getUsername(),
                role.charAt(0) + role.substring(1).toLowerCase(Locale.ROOT),
                displayName.substring(0, displayName.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT),
                principal.role() == AppUserRole.ADMIN
        );
    }

    @ModelAttribute("currentPath")
    String currentPath(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    @ModelAttribute("uiLocaleOptions")
    List<UiLocaleSettings.Option> uiLocaleOptions() {
        return uiLocaleService.options();
    }

    /**
     * The language this page is being written in, whoever decided it. The picker marks it
     * as current even when it came from the browser rather than from the account.
     */
    @ModelAttribute("currentUiLocale")
    String currentUiLocale() {
        return LocaleContextHolder.getLocale().toLanguageTag();
    }

    public record Viewer(String displayName, String email, String role, String initial, boolean admin) {
    }
}
