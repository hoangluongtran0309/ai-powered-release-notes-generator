package com.hoangluongtran0309.releaseflow.configuration;

import com.hoangluongtran0309.releaseflow.account.DuplicateEmailException;
import com.hoangluongtran0309.releaseflow.account.RegistrationApiController;
import com.hoangluongtran0309.releaseflow.account.SessionApiController;
import com.hoangluongtran0309.releaseflow.project.GitHubIntegrationAlreadyConfiguredException;
import com.hoangluongtran0309.releaseflow.project.GitHubRepositoryAlreadyConnectedException;
import com.hoangluongtran0309.releaseflow.project.ProjectApiController;
import com.hoangluongtran0309.releaseflow.project.ProjectNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes = {
        RegistrationApiController.class,
        SessionApiController.class,
        ProjectApiController.class
})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validationFailed(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage())
        );

        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                "One or more request fields are invalid.",
                "validation_failed"
        );
        problem.setProperty("errors", errors);
        return response(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadableRequest() {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                "Malformed request",
                "The request body is missing or malformed.",
                "malformed_request"
        ));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    ResponseEntity<ProblemDetail> duplicateEmail(DuplicateEmailException exception) {
        return response(problem(
                HttpStatus.CONFLICT,
                "Email already registered",
                exception.getMessage(),
                "email_already_registered"
        ));
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    ResponseEntity<ProblemDetail> projectNotFound(ProjectNotFoundException exception) {
        return response(problem(
                HttpStatus.NOT_FOUND,
                "Project not found",
                exception.getMessage(),
                "project_not_found"
        ));
    }

    @ExceptionHandler(GitHubIntegrationAlreadyConfiguredException.class)
    ResponseEntity<ProblemDetail> integrationAlreadyConfigured(
            GitHubIntegrationAlreadyConfiguredException exception
    ) {
        return response(problem(
                HttpStatus.CONFLICT,
                "GitHub integration already configured",
                exception.getMessage(),
                "github_integration_already_configured"
        ));
    }

    @ExceptionHandler(GitHubRepositoryAlreadyConnectedException.class)
    ResponseEntity<ProblemDetail> repositoryAlreadyConnected(
            GitHubRepositoryAlreadyConnectedException exception
    ) {
        return response(problem(
                HttpStatus.CONFLICT,
                "GitHub repository already connected",
                exception.getMessage(),
                "github_repository_already_connected"
        ));
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }

    private static ResponseEntity<ProblemDetail> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
