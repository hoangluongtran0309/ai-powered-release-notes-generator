package com.hoangluongtran0309.releaseflow.configuration;

import com.hoangluongtran0309.releaseflow.account.AppUserRole;
import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.account.UiLocaleService;
import com.hoangluongtran0309.releaseflow.account.UiLocaleSettings;
import com.hoangluongtran0309.releaseflow.overview.OverviewService;
import com.hoangluongtran0309.releaseflow.overview.OverviewView;
import com.hoangluongtran0309.releaseflow.overview.SelectedProject;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@ControllerAdvice(annotations = Controller.class)
class PageModelAdvice {

    private final UiLocaleService uiLocaleService;
    private final OverviewService overviewService;
    private final SelectedProject selectedProject;

    PageModelAdvice(
            UiLocaleService uiLocaleService,
            OverviewService overviewService,
            SelectedProject selectedProject
    ) {
        this.uiLocaleService = uiLocaleService;
        this.overviewService = overviewService;
        this.selectedProject = selectedProject;
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

    /**
     * The Projects the header lets somebody switch between, with the one this page is
     * about marked. Empty for an anonymous page, which reads no account and no Project.
     */
    @ModelAttribute("projectSwitcher")
    List<OverviewView.ProjectOption> projectSwitcher(Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ReleaseFlowPrincipal principal)) {
            return List.of();
        }
        UUID fromUrl = fromQuery(request);
        UUID requested = fromUrl != null ? fromUrl : selectedProject.remembered(request).orElse(null);
        return overviewService.projectOptions(principal.organizationId(), requested);
    }

    private static UUID fromQuery(HttpServletRequest request) {
        String value = request.getParameter("project");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            // A page that cannot read its own parameter reports that itself; the header
            // simply falls back to the remembered Project.
            return null;
        }
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
