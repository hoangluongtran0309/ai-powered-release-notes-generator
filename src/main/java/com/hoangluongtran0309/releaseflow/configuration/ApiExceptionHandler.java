package com.hoangluongtran0309.releaseflow.configuration;

import com.hoangluongtran0309.releaseflow.account.DuplicateEmailException;
import com.hoangluongtran0309.releaseflow.account.InvalidOutputLanguageException;
import com.hoangluongtran0309.releaseflow.account.InvalidInvitationException;
import com.hoangluongtran0309.releaseflow.account.InvitationAlreadyPendingException;
import com.hoangluongtran0309.releaseflow.account.InvitationEmailUnavailableException;
import com.hoangluongtran0309.releaseflow.account.InvitationNotFoundException;
import com.hoangluongtran0309.releaseflow.account.OrganizationMemberApiController;
import com.hoangluongtran0309.releaseflow.account.OutputLanguageApiController;
import com.hoangluongtran0309.releaseflow.account.PublicInvitationApiController;
import com.hoangluongtran0309.releaseflow.account.RegistrationApiController;
import com.hoangluongtran0309.releaseflow.account.SessionApiController;
import com.hoangluongtran0309.releaseflow.change.AiClassificationFailedException;
import com.hoangluongtran0309.releaseflow.change.AiClassificationUnavailableException;
import com.hoangluongtran0309.releaseflow.change.ChangeApiController;
import com.hoangluongtran0309.releaseflow.change.ChangeNotEligibleForAiException;
import com.hoangluongtran0309.releaseflow.change.ChangeNotFoundException;
import com.hoangluongtran0309.releaseflow.change.ChangeProcessingException;
import com.hoangluongtran0309.releaseflow.change.GitHubWebhookController;
import com.hoangluongtran0309.releaseflow.change.InvalidChangeFilterException;
import com.hoangluongtran0309.releaseflow.change.InvalidChangeReviewException;
import com.hoangluongtran0309.releaseflow.change.MalformedWebhookPayloadException;
import com.hoangluongtran0309.releaseflow.change.WebhookRepositoryMismatchException;
import com.hoangluongtran0309.releaseflow.change.WebhookSignatureInvalidException;
import com.hoangluongtran0309.releaseflow.project.GitHubIntegrationAlreadyConfiguredException;
import com.hoangluongtran0309.releaseflow.project.GitHubIntegrationNotFoundException;
import com.hoangluongtran0309.releaseflow.project.GitHubRepositoryAlreadyConnectedException;
import com.hoangluongtran0309.releaseflow.project.GitHubTokenRejectedException;
import com.hoangluongtran0309.releaseflow.project.GitHubUnavailableException;
import com.hoangluongtran0309.releaseflow.project.ProjectApiController;
import com.hoangluongtran0309.releaseflow.project.ProjectNotFoundException;
import com.hoangluongtran0309.releaseflow.release.ChangeNotReleasableException;
import com.hoangluongtran0309.releaseflow.release.ClassificationChangedException;
import com.hoangluongtran0309.releaseflow.release.InvalidReleaseScheduleException;
import com.hoangluongtran0309.releaseflow.release.ReleaseApiController;
import com.hoangluongtran0309.releaseflow.release.ReleaseEmptyException;
import com.hoangluongtran0309.releaseflow.release.ReleaseNotFoundException;
import com.hoangluongtran0309.releaseflow.release.ReleasePublishedException;
import com.hoangluongtran0309.releaseflow.release.ReleaseReviewIncompleteException;
import com.hoangluongtran0309.releaseflow.release.ReleaseStatusException;
import com.hoangluongtran0309.releaseflow.release.ReleaseVersionTakenException;
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
        OrganizationMemberApiController.class,
        OutputLanguageApiController.class,
        PublicInvitationApiController.class,
        ProjectApiController.class,
        GitHubWebhookController.class,
        ChangeApiController.class,
        ReleaseApiController.class
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

    @ExceptionHandler(InvalidOutputLanguageException.class)
    ResponseEntity<ProblemDetail> invalidOutputLanguage(InvalidOutputLanguageException exception) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                "Invalid output language",
                exception.getMessage(),
                "output_language_invalid"
        ));
    }

    @ExceptionHandler(InvalidInvitationException.class)
    ResponseEntity<ProblemDetail> invalidInvitation(InvalidInvitationException exception) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                "Invalid invitation",
                exception.getMessage(),
                "invitation_invalid"
        ));
    }

    @ExceptionHandler(InvitationEmailUnavailableException.class)
    ResponseEntity<ProblemDetail> invitationEmailUnavailable(InvitationEmailUnavailableException exception) {
        return response(problem(
                HttpStatus.CONFLICT,
                "Email unavailable",
                exception.getMessage(),
                "invitation_email_unavailable"
        ));
    }

    @ExceptionHandler(InvitationAlreadyPendingException.class)
    ResponseEntity<ProblemDetail> invitationAlreadyPending(InvitationAlreadyPendingException exception) {
        return response(problem(
                HttpStatus.CONFLICT,
                "Invitation already pending",
                exception.getMessage(),
                "invitation_already_pending"
        ));
    }

    @ExceptionHandler(InvitationNotFoundException.class)
    ResponseEntity<ProblemDetail> invitationNotFound(InvitationNotFoundException exception) {
        return response(problem(
                HttpStatus.NOT_FOUND,
                "Invitation not found",
                exception.getMessage(),
                "invitation_not_found"
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

    @ExceptionHandler(GitHubIntegrationNotFoundException.class)
    ResponseEntity<ProblemDetail> integrationNotFound(GitHubIntegrationNotFoundException exception) {
        return response(problem(
                HttpStatus.NOT_FOUND,
                "GitHub integration not found",
                exception.getMessage(),
                "github_integration_not_found"
        ));
    }

    @ExceptionHandler(GitHubTokenRejectedException.class)
    ResponseEntity<ProblemDetail> gitHubTokenRejected(GitHubTokenRejectedException exception) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                "GitHub token rejected",
                exception.getMessage(),
                "github_token_rejected"
        ));
    }

    @ExceptionHandler(GitHubUnavailableException.class)
    ResponseEntity<ProblemDetail> gitHubUnavailable(GitHubUnavailableException exception) {
        return response(problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "GitHub unavailable",
                exception.getMessage(),
                "github_unavailable"
        ));
    }

    @ExceptionHandler(ChangeProcessingException.class)
    ResponseEntity<ProblemDetail> changeProcessing(ChangeProcessingException exception) {
        return response(problem(
                HttpStatus.CONFLICT,
                "Change still processing",
                exception.getMessage(),
                "change_processing"
        ));
    }

    @ExceptionHandler(ReleaseNotFoundException.class)
    ResponseEntity<ProblemDetail> releaseNotFound(ReleaseNotFoundException exception) {
        return response(problem(
                HttpStatus.NOT_FOUND,
                "Release not found",
                exception.getMessage(),
                "release_not_found"
        ));
    }

    @ExceptionHandler(ReleaseStatusException.class)
    ResponseEntity<ProblemDetail> releaseStatusConflict(ReleaseStatusException exception) {
        return response(problem(
                HttpStatus.CONFLICT,
                "Release status conflict",
                exception.getMessage(),
                "release_status_conflict"
        ));
    }

    @ExceptionHandler(ReleaseReviewIncompleteException.class)
    ResponseEntity<ProblemDetail> releaseReviewIncomplete(ReleaseReviewIncompleteException exception) {
        return response(problem(
                HttpStatus.CONFLICT,
                "Release review incomplete",
                exception.getMessage(),
                "release_review_incomplete"
        ));
    }

    @ExceptionHandler(ClassificationChangedException.class)
    ResponseEntity<ProblemDetail> classificationChanged(ClassificationChangedException exception) {
        return response(problem(
                HttpStatus.CONFLICT,
                "Classification changed",
                exception.getMessage(),
                "classification_changed"
        ));
    }

    @ExceptionHandler(InvalidReleaseScheduleException.class)
    ResponseEntity<ProblemDetail> invalidReleaseSchedule(InvalidReleaseScheduleException exception) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                "Invalid release schedule",
                exception.getMessage(),
                "invalid_release_schedule"
        ));
    }

    @ExceptionHandler(ReleasePublishedException.class)
    ResponseEntity<ProblemDetail> releasePublished(ReleasePublishedException exception) {
        return response(problem(
                HttpStatus.CONFLICT,
                "Release published",
                exception.getMessage(),
                "release_published"
        ));
    }

    @ExceptionHandler(ReleaseEmptyException.class)
    ResponseEntity<ProblemDetail> releaseEmpty(ReleaseEmptyException exception) {
        return response(problem(
                HttpStatus.CONFLICT,
                "Release empty",
                exception.getMessage(),
                "release_empty"
        ));
    }

    @ExceptionHandler(ReleaseVersionTakenException.class)
    ResponseEntity<ProblemDetail> releaseVersionTaken(ReleaseVersionTakenException exception) {
        return response(problem(
                HttpStatus.CONFLICT,
                "Release version taken",
                exception.getMessage(),
                "release_version_taken"
        ));
    }

    @ExceptionHandler(ChangeNotReleasableException.class)
    ResponseEntity<ProblemDetail> changeNotReleasable(ChangeNotReleasableException exception) {
        return response(problem(
                HttpStatus.CONFLICT,
                "Change not releasable",
                exception.getMessage(),
                "change_not_releasable"
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

    @ExceptionHandler(InvalidChangeReviewException.class)
    ResponseEntity<ProblemDetail> invalidChangeReview(InvalidChangeReviewException exception) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                "Invalid change review",
                exception.getMessage(),
                "invalid_change_review"
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
