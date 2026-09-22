package com.hoangluongtran0309.releaseflow.configuration;

import com.hoangluongtran0309.releaseflow.account.DuplicateEmailException;
import com.hoangluongtran0309.releaseflow.account.InvalidOutputLanguageException;
import com.hoangluongtran0309.releaseflow.account.InvalidUiLocaleException;
import com.hoangluongtran0309.releaseflow.account.InvalidInvitationException;
import com.hoangluongtran0309.releaseflow.account.InvitationAlreadyPendingException;
import com.hoangluongtran0309.releaseflow.account.InvitationEmailUnavailableException;
import com.hoangluongtran0309.releaseflow.account.InvitationNotFoundException;
import com.hoangluongtran0309.releaseflow.account.OrganizationMemberApiController;
import com.hoangluongtran0309.releaseflow.account.InvalidOrganizationSlugException;
import com.hoangluongtran0309.releaseflow.account.OrganizationSlugApiController;
import com.hoangluongtran0309.releaseflow.account.OrganizationSlugTakenException;
import com.hoangluongtran0309.releaseflow.account.OutputLanguageApiController;
import com.hoangluongtran0309.releaseflow.account.PublicInvitationApiController;
import com.hoangluongtran0309.releaseflow.account.RegistrationApiController;
import com.hoangluongtran0309.releaseflow.account.SessionApiController;
import com.hoangluongtran0309.releaseflow.account.UiLocaleApiController;
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
        UiLocaleApiController.class,
        OrganizationSlugApiController.class,
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

    private final UiMessages messages;

    ApiExceptionHandler(UiMessages messages) {
        this.messages = messages;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validationFailed(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage())
        );

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation_failed");
        problem.setProperty("errors", errors);
        return response(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadableRequest() {
        return response(problem(HttpStatus.BAD_REQUEST, "malformed_request"));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    ResponseEntity<ProblemDetail> duplicateEmail(DuplicateEmailException exception) {
        return localized(HttpStatus.CONFLICT, "email_already_registered", exception);
    }

    @ExceptionHandler(InvalidOutputLanguageException.class)
    ResponseEntity<ProblemDetail> invalidOutputLanguage(InvalidOutputLanguageException exception) {
        return localized(HttpStatus.BAD_REQUEST, "output_language_invalid", exception);
    }

    @ExceptionHandler(InvalidUiLocaleException.class)
    ResponseEntity<ProblemDetail> invalidUiLocale(InvalidUiLocaleException exception) {
        return localized(HttpStatus.BAD_REQUEST, "ui_locale_invalid", exception);
    }

    @ExceptionHandler(InvalidInvitationException.class)
    ResponseEntity<ProblemDetail> invalidInvitation(InvalidInvitationException exception) {
        return localized(HttpStatus.BAD_REQUEST, "invitation_invalid", exception);
    }

    @ExceptionHandler(InvitationEmailUnavailableException.class)
    ResponseEntity<ProblemDetail> invitationEmailUnavailable(InvitationEmailUnavailableException exception) {
        return localized(HttpStatus.CONFLICT, "invitation_email_unavailable", exception);
    }

    @ExceptionHandler(InvitationAlreadyPendingException.class)
    ResponseEntity<ProblemDetail> invitationAlreadyPending(InvitationAlreadyPendingException exception) {
        return localized(HttpStatus.CONFLICT, "invitation_already_pending", exception);
    }

    @ExceptionHandler(InvitationNotFoundException.class)
    ResponseEntity<ProblemDetail> invitationNotFound(InvitationNotFoundException exception) {
        return localized(HttpStatus.NOT_FOUND, "invitation_not_found", exception);
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    ResponseEntity<ProblemDetail> projectNotFound(ProjectNotFoundException exception) {
        return localized(HttpStatus.NOT_FOUND, "project_not_found", exception);
    }

    @ExceptionHandler(SourceAlreadyConnectedException.class)
    ResponseEntity<ProblemDetail> sourceAlreadyConnected(SourceAlreadyConnectedException exception) {
        return localized(HttpStatus.CONFLICT, exception.code(), exception);
    }

    @ExceptionHandler(SourceNotFoundException.class)
    ResponseEntity<ProblemDetail> sourceNotFound(SourceNotFoundException exception) {
        return localized(HttpStatus.NOT_FOUND, "source_not_found", exception);
    }

    @ExceptionHandler(SourceTokenRejectedException.class)
    ResponseEntity<ProblemDetail> sourceTokenRejected(SourceTokenRejectedException exception) {
        return localized(HttpStatus.BAD_REQUEST, exception.code(), exception);
    }

    @ExceptionHandler(SourceUnavailableException.class)
    ResponseEntity<ProblemDetail> sourceUnavailable(SourceUnavailableException exception) {
        return localized(HttpStatus.SERVICE_UNAVAILABLE, exception.code(), exception);
    }

    @ExceptionHandler(InvalidGitLabBaseUrlException.class)
    ResponseEntity<ProblemDetail> invalidGitLabBaseUrl(InvalidGitLabBaseUrlException exception) {
        return localized(HttpStatus.BAD_REQUEST, "gitlab_base_url_invalid", exception);
    }

    @ExceptionHandler(GitLabHostNotAllowedException.class)
    ResponseEntity<ProblemDetail> gitLabHostNotAllowed(GitLabHostNotAllowedException exception) {
        return localized(HttpStatus.BAD_REQUEST, "gitlab_host_not_allowed", exception);
    }

    @ExceptionHandler(InvalidJiraSiteException.class)
    ResponseEntity<ProblemDetail> invalidJiraSite(InvalidJiraSiteException exception) {
        return localized(HttpStatus.BAD_REQUEST, "jira_site_invalid", exception);
    }

    @ExceptionHandler(ChangeProcessingException.class)
    ResponseEntity<ProblemDetail> changeProcessing(ChangeProcessingException exception) {
        return localized(HttpStatus.CONFLICT, "change_processing", exception);
    }

    @ExceptionHandler(ReleaseNotFoundException.class)
    ResponseEntity<ProblemDetail> releaseNotFound(ReleaseNotFoundException exception) {
        return localized(HttpStatus.NOT_FOUND, "release_not_found", exception);
    }

    @ExceptionHandler(ReleaseStatusException.class)
    ResponseEntity<ProblemDetail> releaseStatusConflict(ReleaseStatusException exception) {
        return localized(HttpStatus.CONFLICT, "release_status_conflict", exception);
    }

    @ExceptionHandler(ReleaseReviewIncompleteException.class)
    ResponseEntity<ProblemDetail> releaseReviewIncomplete(ReleaseReviewIncompleteException exception) {
        return localized(HttpStatus.CONFLICT, "release_review_incomplete", exception);
    }

    @ExceptionHandler(ClassificationChangedException.class)
    ResponseEntity<ProblemDetail> classificationChanged(ClassificationChangedException exception) {
        return localized(HttpStatus.CONFLICT, "classification_changed", exception);
    }

    @ExceptionHandler(InvalidReleaseScheduleException.class)
    ResponseEntity<ProblemDetail> invalidReleaseSchedule(InvalidReleaseScheduleException exception) {
        return localized(HttpStatus.BAD_REQUEST, "invalid_release_schedule", exception);
    }

    @ExceptionHandler(ReleasePublishedException.class)
    ResponseEntity<ProblemDetail> releasePublished(ReleasePublishedException exception) {
        return localized(HttpStatus.CONFLICT, "release_published", exception);
    }

    @ExceptionHandler(ReleaseEmptyException.class)
    ResponseEntity<ProblemDetail> releaseEmpty(ReleaseEmptyException exception) {
        return localized(HttpStatus.CONFLICT, "release_empty", exception);
    }

    @ExceptionHandler(ReleaseVersionTakenException.class)
    ResponseEntity<ProblemDetail> releaseVersionTaken(ReleaseVersionTakenException exception) {
        return localized(HttpStatus.CONFLICT, "release_version_taken", exception);
    }

    @ExceptionHandler(ChangeNotReleasableException.class)
    ResponseEntity<ProblemDetail> changeNotReleasable(ChangeNotReleasableException exception) {
        return localized(HttpStatus.CONFLICT, "change_not_releasable", exception);
    }

    @ExceptionHandler(ChangeNotFoundException.class)
    ResponseEntity<ProblemDetail> changeNotFound(ChangeNotFoundException exception) {
        return localized(HttpStatus.NOT_FOUND, "change_not_found", exception);
    }

    @ExceptionHandler(ChangeNotEligibleForAiException.class)
    ResponseEntity<ProblemDetail> changeNotEligibleForAi(ChangeNotEligibleForAiException exception) {
        return localized(HttpStatus.CONFLICT, "change_not_eligible_for_ai", exception);
    }

    @ExceptionHandler(AiClassificationFailedException.class)
    ResponseEntity<ProblemDetail> aiClassificationFailed(AiClassificationFailedException exception) {
        return localized(HttpStatus.BAD_GATEWAY, "ai_classification_failed", exception);
    }

    @ExceptionHandler(AiClassificationUnavailableException.class)
    ResponseEntity<ProblemDetail> aiClassificationUnavailable(AiClassificationUnavailableException exception) {
        return localized(HttpStatus.SERVICE_UNAVAILABLE, "ai_classification_unavailable", exception);
    }

    @ExceptionHandler(InvalidChangeReviewException.class)
    ResponseEntity<ProblemDetail> invalidChangeReview(InvalidChangeReviewException exception) {
        return localized(HttpStatus.BAD_REQUEST, "invalid_change_review", exception);
    }

    @ExceptionHandler(InvalidChangeFilterException.class)
    ResponseEntity<ProblemDetail> invalidChangeFilter(InvalidChangeFilterException exception) {
        return localized(HttpStatus.BAD_REQUEST, "invalid_change_filter", exception);
    }

    @ExceptionHandler(InvalidOrganizationSlugException.class)
    ResponseEntity<ProblemDetail> invalidOrganizationSlug(InvalidOrganizationSlugException exception) {
        return localized(HttpStatus.BAD_REQUEST, "organization_slug_invalid", exception);
    }

    @ExceptionHandler(OrganizationSlugTakenException.class)
    ResponseEntity<ProblemDetail> organizationSlugTaken(OrganizationSlugTakenException exception) {
        return localized(HttpStatus.CONFLICT, "organization_slug_taken", exception);
    }

    @ExceptionHandler(WebhookSignatureInvalidException.class)
    ResponseEntity<ProblemDetail> webhookSignatureInvalid(WebhookSignatureInvalidException exception) {
        return localized(HttpStatus.UNAUTHORIZED, "webhook_signature_invalid", exception);
    }

    @ExceptionHandler(WebhookRepositoryMismatchException.class)
    ResponseEntity<ProblemDetail> webhookRepositoryMismatch(WebhookRepositoryMismatchException exception) {
        return localized(HttpStatus.UNPROCESSABLE_CONTENT, "webhook_repository_mismatch", exception);
    }

    @ExceptionHandler(WebhookPayloadTooLargeException.class)
    ResponseEntity<ProblemDetail> webhookPayloadTooLarge(WebhookPayloadTooLargeException exception) {
        return localized(HttpStatus.PAYLOAD_TOO_LARGE, "webhook_payload_too_large", exception);
    }

    @ExceptionHandler(MalformedWebhookPayloadException.class)
    ResponseEntity<ProblemDetail> malformedWebhookPayload(MalformedWebhookPayloadException exception) {
        return localized(HttpStatus.BAD_REQUEST, "webhook_payload_malformed", exception);
    }

    @ExceptionHandler(ReleaseNoteNotFoundException.class)
    ResponseEntity<ProblemDetail> releaseNoteNotFound(ReleaseNoteNotFoundException exception) {
        return localized(HttpStatus.NOT_FOUND, "release_note_not_found", exception);
    }

    @ExceptionHandler(ReleaseNotesMissingException.class)
    ResponseEntity<ProblemDetail> releaseNotesMissing(ReleaseNotesMissingException exception) {
        return localized(HttpStatus.CONFLICT, "release_notes_missing", exception);
    }

    @ExceptionHandler(ReleaseNoteRenderException.class)
    ResponseEntity<ProblemDetail> releaseNoteRenderFailed(ReleaseNoteRenderException exception) {
        return localized(HttpStatus.CONFLICT, "release_note_render_failed", exception);
    }

    @ExceptionHandler(AudienceNotFoundException.class)
    ResponseEntity<ProblemDetail> audienceNotFound(AudienceNotFoundException exception) {
        return localized(HttpStatus.NOT_FOUND, "audience_not_found", exception);
    }

    @ExceptionHandler(AudienceConflictException.class)
    ResponseEntity<ProblemDetail> audienceConflict(AudienceConflictException exception) {
        return localized(HttpStatus.CONFLICT, exception.code(), exception);
    }

    @ExceptionHandler(InvalidAudienceTemplateException.class)
    ResponseEntity<ProblemDetail> invalidAudienceTemplate(InvalidAudienceTemplateException exception) {
        return localized(HttpStatus.BAD_REQUEST, exception.code(), exception);
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    ResponseEntity<ProblemDetail> categoryNotFound(CategoryNotFoundException exception) {
        return localized(HttpStatus.NOT_FOUND, "category_not_found", exception);
    }

    @ExceptionHandler(CategorySuggestionNotFoundException.class)
    ResponseEntity<ProblemDetail> categorySuggestionNotFound(CategorySuggestionNotFoundException exception) {
        return localized(HttpStatus.NOT_FOUND, "category_suggestion_not_found", exception);
    }

    @ExceptionHandler(CategoryConflictException.class)
    ResponseEntity<ProblemDetail> categoryConflict(CategoryConflictException exception) {
        return localized(HttpStatus.CONFLICT, exception.code(), exception);
    }

    @ExceptionHandler(DuplicateCandidateNotFoundException.class)
    ResponseEntity<ProblemDetail> duplicateCandidateNotFound(DuplicateCandidateNotFoundException exception) {
        return localized(HttpStatus.NOT_FOUND, "duplicate_candidate_not_found", exception);
    }

    @ExceptionHandler(DuplicateCandidateDecidedException.class)
    ResponseEntity<ProblemDetail> duplicateCandidateDecided(DuplicateCandidateDecidedException exception) {
        return localized(HttpStatus.CONFLICT, "duplicate_candidate_decided", exception);
    }

    @ExceptionHandler(InvalidSensitivePathsException.class)
    ResponseEntity<ProblemDetail> invalidSensitivePaths(InvalidSensitivePathsException exception) {
        return localized(HttpStatus.BAD_REQUEST, "invalid_sensitive_paths", exception);
    }

    @ExceptionHandler(InvalidReleaseLanguagesException.class)
    ResponseEntity<ProblemDetail> invalidReleaseLanguages(InvalidReleaseLanguagesException exception) {
        return localized(HttpStatus.BAD_REQUEST, "invalid_release_languages", exception);
    }

    @ExceptionHandler(TranslationsNotReadyException.class)
    ResponseEntity<ProblemDetail> translationsNotReady(TranslationsNotReadyException exception) {
        return localized(HttpStatus.CONFLICT, "translations_not_ready", exception);
    }

    @ExceptionHandler(SourceTokenMissingException.class)
    ResponseEntity<ProblemDetail> sourceTokenMissing(SourceTokenMissingException exception) {
        return localized(HttpStatus.CONFLICT, "source_token_missing", exception);
    }

    @ExceptionHandler(SourceSyncInProgressException.class)
    ResponseEntity<ProblemDetail> sourceSyncInProgress(SourceSyncInProgressException exception) {
        return localized(HttpStatus.CONFLICT, "source_sync_in_progress", exception);
    }

    @ExceptionHandler(SourceImportNotSupportedException.class)
    ResponseEntity<ProblemDetail> sourceImportNotSupported(SourceImportNotSupportedException exception) {
        return localized(HttpStatus.CONFLICT, "source_import_not_supported", exception);
    }

    @ExceptionHandler(SourceImportNotResumableException.class)
    ResponseEntity<ProblemDetail> sourceImportNotResumable(SourceImportNotResumableException exception) {
        return localized(HttpStatus.CONFLICT, "source_import_not_resumable", exception);
    }

    @ExceptionHandler(AutomationRuleNotFoundException.class)
    ResponseEntity<ProblemDetail> automationRuleNotFound(AutomationRuleNotFoundException exception) {
        return localized(HttpStatus.NOT_FOUND, "automation_rule_not_found", exception);
    }

    @ExceptionHandler(AutomationRunNotFoundException.class)
    ResponseEntity<ProblemDetail> automationRunNotFound(AutomationRunNotFoundException exception) {
        return localized(HttpStatus.NOT_FOUND, "automation_run_not_found", exception);
    }

    @ExceptionHandler(AutomationConflictException.class)
    ResponseEntity<ProblemDetail> automationConflict(AutomationConflictException exception) {
        return localized(HttpStatus.CONFLICT, exception.code(), exception);
    }

    @ExceptionHandler(AutomationActionInvalidException.class)
    ResponseEntity<ProblemDetail> automationActionInvalid(AutomationActionInvalidException exception) {
        return localized(HttpStatus.BAD_REQUEST, exception.code(), exception);
    }

    @ExceptionHandler(InvalidAutomationRunPageException.class)
    ResponseEntity<ProblemDetail> invalidAutomationRunPage(InvalidAutomationRunPageException exception) {
        return localized(HttpStatus.BAD_REQUEST, "invalid_automation_run_page", exception);
    }

    /**
     * The same failure in the reader's own language. The {@code code} is what a caller
     * matches on and never changes; only the title and the detail are translated, and the
     * detail is the key the failure itself carried.
     */
    private ResponseEntity<ProblemDetail> localized(HttpStatus status, String code, LocalizedException exception) {
        return response(problem(status, code, messages.of(exception)));
    }

    /** A failure whose only wording is the one its code stands for. */
    private ProblemDetail problem(HttpStatus status, String code) {
        return problem(status, code, messages.get("error." + code));
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(messages.get("error." + code + ".title"));
        problem.setProperty("code", code);
        return problem;
    }

    private static ResponseEntity<ProblemDetail> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
