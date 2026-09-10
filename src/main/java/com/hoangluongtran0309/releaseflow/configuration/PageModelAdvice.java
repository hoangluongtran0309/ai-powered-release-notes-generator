package com.hoangluongtran0309.releaseflow.configuration;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Locale;

@ControllerAdvice(annotations = Controller.class)
class PageModelAdvice {

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
                displayName.substring(0, displayName.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT)
        );
    }

    @ModelAttribute("currentPath")
    String currentPath(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    public record Viewer(String displayName, String email, String role, String initial) {
    }
}
