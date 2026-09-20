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
import com.hoangluongtran0309.releaseflow.audience.AudienceApiController;
import com.hoangluongtran0309.releaseflow.audience.AudienceConflictException;
import com.hoangluongtran0309.releaseflow.audience.AudienceNotFoundException;
import com.hoangluongtran0309.releaseflow.audience.InvalidAudienceTemplateException;
import com.hoangluongtran0309.releaseflow.audience.InvalidReleaseLanguagesException;
import com.hoangluongtran0309.releaseflow.audience.ReleaseLanguageApiController;
import com.hoangluongtran0309.releaseflow.automation.AutomationActionInvalidException;
import com.hoangluongtran0309.releaseflow.automation.AutomationConflictException;
import com.hoangluongtran0309.releaseflow.automation.AutomationRuleApiController;
import com.hoangluongtran0309.releaseflow.automation.AutomationRuleNotFoundException;
import com.hoangluongtran0309.releaseflow.automation.AutomationRunApiController;
import com.hoangluongtran0309.releaseflow.automation.AutomationWebhookController;
import com.hoangluongtran0309.releaseflow.automation.AutomationRunNotFoundException;
import com.hoangluongtran0309.releaseflow.automation.InvalidAutomationRunPageException;
import com.hoangluongtran0309.releaseflow.category.CategoryApiController;
import com.hoangluongtran0309.releaseflow.category.CategoryConflictException;
import com.hoangluongtran0309.releaseflow.category.CategoryNotFoundException;
import com.hoangluongtran0309.releaseflow.category.CategorySuggestionNotFoundException;
import com.hoangluongtran0309.releaseflow.change.AiClassificationFailedException;
import com.hoangluongtran0309.releaseflow.change.AiClassificationUnavailableException;
import com.hoangluongtran0309.releaseflow.change.ChangeApiController;
import com.hoangluongtran0309.releaseflow.change.ChangeNotEligibleForAiException;
import com.hoangluongtran0309.releaseflow.change.ChangeNotFoundException;
import com.hoangluongtran0309.releaseflow.change.ChangeProcessingException;
import com.hoangluongtran0309.releaseflow.change.DuplicateCandidateDecidedException;
import com.hoangluongtran0309.releaseflow.change.DuplicateCandidateNotFoundException;
import com.hoangluongtran0309.releaseflow.change.GitHubWebhookController;
import com.hoangluongtran0309.releaseflow.change.GitLabWebhookController;
import com.hoangluongtran0309.releaseflow.change.LinearWebhookController;
import com.hoangluongtran0309.releaseflow.change.InvalidChangeFilterException;
import com.hoangluongtran0309.releaseflow.change.InvalidChangeReviewException;
import com.hoangluongtran0309.releaseflow.change.InvalidSensitivePathsException;
import com.hoangluongtran0309.releaseflow.change.MalformedWebhookPayloadException;
import com.hoangluongtran0309.releaseflow.change.SensitivePathApiController;
import com.hoangluongtran0309.releaseflow.change.SourceImportApiController;
import com.hoangluongtran0309.releaseflow.change.SourceImportNotResumableException;
import com.hoangluongtran0309.releaseflow.change.SourceImportNotSupportedException;
import com.hoangluongtran0309.releaseflow.change.SourceSyncInProgressException;
import com.hoangluongtran0309.releaseflow.change.SourceTokenMissingException;
import com.hoangluongtran0309.releaseflow.change.WebhookPayloadTooLargeException;
import com.hoangluongtran0309.releaseflow.change.WebhookRepositoryMismatchException;
import com.hoangluongtran0309.releaseflow.change.WebhookSignatureInvalidException;
import com.hoangluongtran0309.releaseflow.gitlab.GitLabHostNotAllowedException;
import com.hoangluongtran0309.releaseflow.gitlab.InvalidGitLabBaseUrlException;
import com.hoangluongtran0309.releaseflow.jira.InvalidJiraSiteException;
import com.hoangluongtran0309.releaseflow.project.ProjectApiController;
import com.hoangluongtran0309.releaseflow.project.ProjectNotFoundException;
import com.hoangluongtran0309.releaseflow.project.SourceAlreadyConnectedException;
import com.hoangluongtran0309.releaseflow.project.SourceNotFoundException;
import com.hoangluongtran0309.releaseflow.project.SourceTokenRejectedException;
import com.hoangluongtran0309.releaseflow.project.SourceUnavailableException;
import com.hoangluongtran0309.releaseflow.release.ChangeNotReleasableException;
import com.hoangluongtran0309.releaseflow.release.ClassificationChangedException;
import com.hoangluongtran0309.releaseflow.release.InvalidReleaseScheduleException;
import com.hoangluongtran0309.releaseflow.release.ReleaseApiController;
import com.hoangluongtran0309.releaseflow.release.ReleaseEmptyException;
import com.hoangluongtran0309.releaseflow.release.ReleaseNotFoundException;
import com.hoangluongtran0309.releaseflow.release.ReleaseNoteNotFoundException;
import com.hoangluongtran0309.releaseflow.release.ReleaseNoteRenderException;
import com.hoangluongtran0309.releaseflow.release.ReleaseNotesMissingException;
import com.hoangluongtran0309.releaseflow.release.ReleasePublishedException;
import com.hoangluongtran0309.releaseflow.release.ReleaseReviewIncompleteException;
import com.hoangluongtran0309.releaseflow.release.ReleaseStatusException;
import com.hoangluongtran0309.releaseflow.release.ReleaseVersionTakenException;
import com.hoangluongtran0309.releaseflow.release.TranslationsNotReadyException;
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
        GitLabWebhookController.class,
        LinearWebhookController.class,
        ChangeApiController.class,
        ReleaseApiController.class,
        AudienceApiController.class,
        CategoryApiController.class,
        SensitivePathApiController.class,
        ReleaseLanguageApiController.class,
        SourceImportApiController.class,
        AutomationRuleApiController.class,
        AutomationRunApiController.class,
        AutomationWebhookController.class
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

    @ExceptionHandler(SourceAlreadyConnectedException.class)
    ResponseEntity<ProblemDetail> sourceAlreadyConnected(SourceAlreadyConnectedException exception) {
        return response(problem(
                HttpStatus.CONFLICT,
                "Source already connected",
                exception.getMessage(),
                exception.code()
        ));
    }

    @ExceptionHandler(SourceNotFoundException.class)
    ResponseEntity<ProblemDetail> sourceNotFound(SourceNotFoundException exception) {
        return response(problem(
                HttpStatus.NOT_FOUND,
                "Source not found",
                exception.getMessage(),
                "source_not_found"
        ));
    }

    @ExceptionHandler(SourceTokenRejectedException.class)
    ResponseEntity<ProblemDetail> sourceTokenRejected(SourceTokenRejectedException exception) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                "Access token rejected",
                exception.getMessage(),
                exception.code()
        ));
    }

    @ExceptionHandler(SourceUnavailableException.class)
    ResponseEntity<ProblemDetail> sourceUnavailable(SourceUnavailableException exception) {
        return response(problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Source unavailable",
                exception.getMessage(),
                exception.code()
        ));
    }

    @ExceptionHandler(InvalidGitLabBaseUrlException.class)
    ResponseEntity<ProblemDetail> invalidGitLabBaseUrl(InvalidGitLabBaseUrlException exception) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                "Invalid GitLab instance URL",
                exception.getMessage(),
                "gitlab_base_url_invalid"
        ));
    }

    @ExceptionHandler(GitLabHostNotAllowedException.class)
    ResponseEntity<ProblemDetail> gitLabHostNotAllowed(GitLabHostNotAllowedException exception) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                "GitLab host not allowed",
                exception.getMessage(),
                "gitlab_host_not_allowed"
        ));
    }

    @ExceptionHandler(InvalidJiraSiteException.class)
    ResponseEntity<ProblemDetail> invalidJiraSite(InvalidJiraSiteException exception) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                "Invalid Jira site URL",
                exception.getMessage(),
                "jira_site_invalid"
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

    @ExceptionHandler(WebhookPayloadTooLargeException.class)
    ResponseEntity<ProblemDetail> webhookPayloadTooLarge(WebhookPayloadTooLargeException exception) {
        return response(problem(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Webhook payload too large",
                exception.getMessage(),
                "webhook_payload_too_large"
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

    @ExceptionHandler(ReleaseNoteNotFoundException.class)
    ResponseEntity<ProblemDetail> releaseNoteNotFound(ReleaseNoteNotFoundException exception) {
        return response(problem(
                HttpStatus.NOT_FOUND,
                "Release note not found",
                exception.getMessage(),
                "release_note_not_found"
        ));
    }

    @ExceptionHandler(ReleaseNotesMissingException.class)
    ResponseEntity<ProblemDetail> releaseNotesMissing(ReleaseNotesMissingException exception) {
        return response(problem(
                HttpStatus.CONFLICT,
                "Release notes missing",
                exception.getMessage(),
                "release_notes_missing"
        ));
    }

    @ExceptionHandler(ReleaseNoteRenderException.class)
    ResponseEntity<ProblemDetail> releaseNoteRenderFailed(ReleaseNoteRenderException exception) {
        return response(problem(
                HttpStatus.CONFLICT,
                "Release note could not be rendered",
                exception.getMessage(),
                "release_note_render_failed"
        ));
    }

    @ExceptionHandler(AudienceNotFoundException.class)
    ResponseEntity<ProblemDetail> audienceNotFound(AudienceNotFoundException exception) {
        return response(problem(
                HttpStatus.NOT_FOUND,
                "Audience not found",
                exception.getMessage(),
                "audience_not_found"
        ));
    }

    @ExceptionHandler(AudienceConflictException.class)
    ResponseEntity<ProblemDetail> audienceConflict(AudienceConflictException exception) {
        return response(problem(
                HttpStatus.CONFLICT,
                "Audience conflict",
                exception.getMessage(),
                exception.code()
        ));
    }

    @ExceptionHandler(InvalidAudienceTemplateException.class)
    ResponseEntity<ProblemDetail> invalidAudienceTemplate(InvalidAudienceTemplateException exception) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                "Invalid audience template",
                exception.getMessage(),
                exception.code()
        ));
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    ResponseEntity<ProblemDetail> categoryNotFound(CategoryNotFoundException exception) {
        return response(problem(HttpStatus.NOT_FOUND, "Category not found", exception.getMessage(), "category_not_found"));
    }

    @ExceptionHandler(CategorySuggestionNotFoundException.class)
    ResponseEntity<ProblemDetail> categorySuggestionNotFound(CategorySuggestionNotFoundException exception) {
        return response(problem(
                HttpStatus.NOT_FOUND,
                "Category suggestion not found",
                exception.getMessage(),
                "category_suggestion_not_found"
        ));
    }

    @ExceptionHandler(CategoryConflictException.class)
    ResponseEntity<ProblemDetail> categoryConflict(CategoryConflictException exception) {
        return response(problem(HttpStatus.CONFLICT, "Category conflict", exception.getMessage(), exception.code()));
    }

    @ExceptionHandler(DuplicateCandidateNotFoundException.class)
    ResponseEntity<ProblemDetail> duplicateCandidateNotFound(DuplicateCandidateNotFoundException exception) {
        return response(problem(
                HttpStatus.NOT_FOUND,
                "Possible duplicate not found",
                exception.getMessage(),
                "duplicate_candidate_not_found"
        ));
    }

    @ExceptionHandler(DuplicateCandidateDecidedException.class)
    ResponseEntity<ProblemDetail> duplicateCandidateDecided(DuplicateCandidateDecidedException exception) {
        return response(problem(
                HttpStatus.CONFLICT,
                "Possible duplicate already decided",
                exception.getMessage(),
                "duplicate_candidate_decided"
        ));
    }

    @ExceptionHandler(InvalidSensitivePathsException.class)
    ResponseEntity<ProblemDetail> invalidSensitivePaths(InvalidSensitivePathsException exception) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                "Invalid sensitive paths",
                exception.getMessage(),
                "invalid_sensitive_paths"
        ));
    }

    @ExceptionHandler(InvalidReleaseLanguagesException.class)
    ResponseEntity<ProblemDetail> invalidReleaseLanguages(InvalidReleaseLanguagesException exception) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                "Invalid release note languages",
                exception.getMessage(),
                "invalid_release_languages"
        ));
    }

    @ExceptionHandler(TranslationsNotReadyException.class)
    ResponseEntity<ProblemDetail> translationsNotReady(TranslationsNotReadyException exception) {
        return response(problem(
                HttpStatus.CONFLICT,
                "Translations not ready",
                exception.getMessage(),
                "translations_not_ready"
        ));
    }

    @ExceptionHandler(SourceTokenMissingException.class)
    ResponseEntity<ProblemDetail> sourceTokenMissing(SourceTokenMissingException exception) {
        return response(problem(HttpStatus.CONFLICT, "Access token missing", exception.getMessage(), "source_token_missing"));
    }

    @ExceptionHandler(SourceSyncInProgressException.class)
    ResponseEntity<ProblemDetail> sourceSyncInProgress(SourceSyncInProgressException exception) {
        return response(problem(HttpStatus.CONFLICT, "Import in progress", exception.getMessage(), "source_sync_in_progress"));
    }

    @ExceptionHandler(SourceImportNotSupportedException.class)
    ResponseEntity<ProblemDetail> sourceImportNotSupported(SourceImportNotSupportedException exception) {
        return response(problem(
                HttpStatus.CONFLICT,
                "Import not supported",
                exception.getMessage(),
                "source_import_not_supported"
        ));
    }

    @ExceptionHandler(SourceImportNotResumableException.class)
    ResponseEntity<ProblemDetail> sourceImportNotResumable(SourceImportNotResumableException exception) {
        return response(problem(
                HttpStatus.CONFLICT,
                "Import not resumable",
                exception.getMessage(),
                "source_import_not_resumable"
        ));
    }

    @ExceptionHandler(AutomationRuleNotFoundException.class)
    ResponseEntity<ProblemDetail> automationRuleNotFound(AutomationRuleNotFoundException exception) {
        return response(problem(
                HttpStatus.NOT_FOUND,
                "Automation rule not found",
                exception.getMessage(),
                "automation_rule_not_found"
        ));
    }

    @ExceptionHandler(AutomationRunNotFoundException.class)
    ResponseEntity<ProblemDetail> automationRunNotFound(AutomationRunNotFoundException exception) {
        return response(problem(
                HttpStatus.NOT_FOUND,
                "Automation run not found",
                exception.getMessage(),
                "automation_run_not_found"
        ));
    }

    @ExceptionHandler(AutomationConflictException.class)
    ResponseEntity<ProblemDetail> automationConflict(AutomationConflictException exception) {
        return response(problem(
                HttpStatus.CONFLICT,
                "Automation conflict",
                exception.getMessage(),
                exception.code()
        ));
    }

    @ExceptionHandler(AutomationActionInvalidException.class)
    ResponseEntity<ProblemDetail> automationActionInvalid(AutomationActionInvalidException exception) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                "Invalid automation rule",
                exception.getMessage(),
                exception.code()
        ));
    }

    @ExceptionHandler(InvalidAutomationRunPageException.class)
    ResponseEntity<ProblemDetail> invalidAutomationRunPage(InvalidAutomationRunPageException exception) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                "Invalid run page",
                exception.getMessage(),
                "invalid_automation_run_page"
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
