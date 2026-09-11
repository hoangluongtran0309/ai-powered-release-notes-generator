package com.hoangluongtran0309.releaseflow.configuration;

import com.hoangluongtran0309.releaseflow.account.DuplicateEmailException;
import com.hoangluongtran0309.releaseflow.account.RegistrationApiController;
import com.hoangluongtran0309.releaseflow.account.SessionApiController;
import com.hoangluongtran0309.releaseflow.change.AiClassificationFailedException;
import com.hoangluongtran0309.releaseflow.change.AiClassificationUnavailableException;
import com.hoangluongtran0309.releaseflow.change.ChangeApiController;
import com.hoangluongtran0309.releaseflow.change.ChangeNotEligibleForAiException;
import com.hoangluongtran0309.releaseflow.change.ChangeNotFoundException;
import com.hoangluongtran0309.releaseflow.change.GitHubWebhookController;
import com.hoangluongtran0309.releaseflow.change.InvalidChangeFilterException;
import com.hoangluongtran0309.releaseflow.change.MalformedWebhookPayloadException;
import com.hoangluongtran0309.releaseflow.change.WebhookRepositoryMismatchException;
import com.hoangluongtran0309.releaseflow.change.WebhookSignatureInvalidException;
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
        ProjectApiController.class,
        GitHubWebhookController.class,
        ChangeApiController.class
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

    @ExceptionHandler(ChangeNotFoundException.class)
    ResponseEntity<ProblemDetail> changeNotFound(ChangeNotFoundException exception) {
        return response(problem(
                HttpStatus.NOT_FOUND,
                "Change not found",
                exception.getMessage(),
                "change_not_found"
        ));
    }

    @ExceptionHandler(ChangeNotEligibleForAiException.class)
    ResponseEntity<ProblemDetail> changeNotEligibleForAi(ChangeNotEligibleForAiException exception) {
        return response(problem(
                HttpStatus.CONFLICT,
                "Change not eligible for AI classification",
                exception.getMessage(),
                "change_not_eligible_for_ai"
        ));
    }

    @ExceptionHandler(AiClassificationFailedException.class)
    ResponseEntity<ProblemDetail> aiClassificationFailed(AiClassificationFailedException exception) {
        return response(problem(
                HttpStatus.BAD_GATEWAY,
                "AI classification failed",
                exception.getMessage(),
                "ai_classification_failed"
        ));
    }

    @ExceptionHandler(AiClassificationUnavailableException.class)
    ResponseEntity<ProblemDetail> aiClassificationUnavailable(AiClassificationUnavailableException exception) {
        return response(problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI classification unavailable",
                exception.getMessage(),
                "ai_classification_unavailable"
        ));
    }

    @ExceptionHandler(InvalidChangeFilterException.class)
    ResponseEntity<ProblemDetail> invalidChangeFilter(InvalidChangeFilterException exception) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                "Invalid change filter",
                exception.getMessage(),
                "invalid_change_filter"
        ));
    }

    @ExceptionHandler(WebhookSignatureInvalidException.class)
    ResponseEntity<ProblemDetail> webhookSignatureInvalid(WebhookSignatureInvalidException exception) {
        return response(problem(
                HttpStatus.UNAUTHORIZED,
                "Webhook signature invalid",
                exception.getMessage(),
                "webhook_signature_invalid"
        ));
    }

    @ExceptionHandler(WebhookRepositoryMismatchException.class)
    ResponseEntity<ProblemDetail> webhookRepositoryMismatch(WebhookRepositoryMismatchException exception) {
        return response(problem(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Webhook repository mismatch",
                exception.getMessage(),
                "webhook_repository_mismatch"
        ));
    }

    @ExceptionHandler(MalformedWebhookPayloadException.class)
    ResponseEntity<ProblemDetail> malformedWebhookPayload(MalformedWebhookPayloadException exception) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                "Webhook payload malformed",
                exception.getMessage(),
                "webhook_payload_malformed"
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
