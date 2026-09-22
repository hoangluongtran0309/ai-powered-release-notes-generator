package com.hoangluongtran0309.releaseflow.configuration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

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
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        problems.write(request, response, HttpServletResponse.SC_FORBIDDEN, "access_denied");
    }
}
