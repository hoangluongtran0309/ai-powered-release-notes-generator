package com.hoangluongtran0309.releaseflow.configuration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Answers a refused request. An API caller gets Problem Details. A browser whose form
 * outlived its session is sent to sign in again, and one whose form is merely stale is
 * told so, rather than hearing that the page belongs to somebody else.
 */
@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    static final String ERROR_REASON = "releaseflow.error.reason";
    static final String FORM_EXPIRED = "formExpired";

    private static final AuthenticationTrustResolver TRUST = new AuthenticationTrustResolverImpl();

    private final ProblemWriter problems;

    ApiAccessDeniedHandler(ProblemWriter problems) {
        this.problems = problems;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        if (!request.getRequestURI().startsWith("/api/")) {
            if (accessDeniedException instanceof CsrfException) {
                // The form never reached a controller, so nothing it asked for was done.
                if (!signedIn()) {
                    response.sendRedirect(request.getContextPath() + "/login?expired");
                    return;
                }
                request.setAttribute(ERROR_REASON, FORM_EXPIRED);
            }
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        problems.write(request, response, HttpServletResponse.SC_FORBIDDEN, "access_denied");
    }

    private static boolean signedIn() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() && !TRUST.isAnonymous(authentication);
    }
}
