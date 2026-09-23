package com.hoangluongtran0309.releaseflow.configuration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.util.InvalidMimeTypeException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * An address that matches nothing. Spring MVC answers this with Problem Details for every
 * caller once {@code spring.mvc.problemdetails.enabled} is on, which is right for a REST
 * client and wrong for somebody who mistyped a URL in a browser: they would be shown raw
 * JSON. This asks what the caller wanted and answers in kind.
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class UnknownAddressAdvice {

    private final UiMessages messages;

    UnknownAddressAdvice(UiMessages messages) {
        this.messages = messages;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    Object notFound(HttpServletRequest request, HttpServletResponse response, Model model) {
        if (!wantsHtml(request)) {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
            problem.setTitle(messages.get("error.not_found.title"));
            problem.setDetail(messages.get("error.not_found"));
            problem.setProperty("code", "not_found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);
        }
        response.setStatus(HttpStatus.NOT_FOUND.value());
        model.addAttribute("status", HttpStatus.NOT_FOUND.value());
        model.addAttribute("statusKey", "notFound");
        model.addAttribute("path", request.getRequestURI());
        return "error";
    }

    // A browser names text/html. A fetch, a REST client or curl sends */* or a JSON
    // type, and an address under /api is an API address whatever it claims to accept.
    private static boolean wantsHtml(HttpServletRequest request) {
        if (request.getRequestURI().startsWith("/api/")) {
            return false;
        }
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept == null) {
            return false;
        }
        try {
            return MediaType.parseMediaTypes(accept).stream()
                    .anyMatch(type -> !MediaType.ALL.equals(type) && type.includes(MediaType.TEXT_HTML));
        } catch (InvalidMimeTypeException exception) {
            return false;
        }
    }
}
