package com.hoangluongtran0309.releaseflow.configuration;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Writes a Problem Details body from the security filter chain, which runs before Spring
 * MVC has put a locale in place. The language is therefore read from the request itself,
 * the same way the resolver would read it while answering.
 */
@Component
class ProblemWriter {

    private final ObjectMapper objectMapper;
    private final LocaleResolver localeResolver;
    private final UiMessages messages;

    ProblemWriter(ObjectMapper objectMapper, LocaleResolver localeResolver, UiMessages messages) {
        this.objectMapper = objectMapper;
        this.localeResolver = localeResolver;
        this.messages = messages;
    }

    void write(HttpServletRequest request, HttpServletResponse response, int status, String code) throws IOException {
        Locale locale = localeResolver.resolveLocale(request);
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(messages.get(locale, "error." + code + ".title"));
        problem.setDetail(messages.get(locale, "error." + code));
        problem.setProperty("code", code);

        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
