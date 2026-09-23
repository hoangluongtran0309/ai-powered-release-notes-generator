package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.account.OutputLanguageService;
import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.audience.AudienceService;
import com.hoangluongtran0309.releaseflow.category.CategoryService;
import com.hoangluongtran0309.releaseflow.change.ChangeNotFoundException;
import com.hoangluongtran0309.releaseflow.change.ChangeProcessingException;
import com.hoangluongtran0309.releaseflow.change.ChangeSummaryRequest;
import com.hoangluongtran0309.releaseflow.change.InvalidChangeReviewException;
import com.hoangluongtran0309.releaseflow.overview.OverviewService;
import com.hoangluongtran0309.releaseflow.overview.SelectedProject;
import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;
import com.hoangluongtran0309.releaseflow.configuration.UiMessages;
import com.hoangluongtran0309.releaseflow.project.ProjectNotFoundException;
import com.hoangluongtran0309.releaseflow.project.ProjectService;
import com.hoangluongtran0309.releaseflow.project.ProjectView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class ReleasePageController {

    private final ProjectService projectService;
    private final ReleaseService releaseService;
    private final AudienceService audienceService;
    private final OutputLanguageService outputLanguageService;
    private final CategoryService categoryService;
    private final UiMessages messages;
    private final SelectedProject selectedProject;
    private final OverviewService overviewService;



    ReleasePageController(
            ProjectService projectService,
            ReleaseService releaseService,
            AudienceService audienceService,
            OutputLanguageService outputLanguageService,
            CategoryService categoryService,
            UiMessages messages,
            SelectedProject selectedProject,
            OverviewService overviewService
    ) {
        this.categoryService = categoryService;
        this.projectService = projectService;
        this.releaseService = releaseService;
        this.audienceService = audienceService;
        this.outputLanguageService = outputLanguageService;
        this.messages = messages;
        this.selectedProject = selectedProject;
        this.overviewService = overviewService;
    }

    // A URL that names no Project opens on the one this person was last looking at.
    @GetMapping("/releases")
    String releases(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @RequestParam(name = "project", required = false) UUID projectId,
            @RequestParam(name = "status", required = false) String status,
            Model model,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        // A URL that names a Project is answered as it stands: one of another Organization
        // is reported as not found, never swapped for one of this Organization's.
        UUID selected = projectId != null
                ? projectId
                : overviewService.rememberedOrFirstProject(
                        principal.organizationId(),
                        selectedProject.remembered(request).orElse(null)
                ).orElse(null);
        if (overviewService.owns(principal.organizationId(), selected)) {
            selectedProject.remember(request, response, selected);
        }
        return renderReleases(principal, selected, status, model, response);
    }

    @PostMapping("/projects/{projectId}/releases")
    String create(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @ModelAttribute("releaseRequest") NewReleaseRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            return renderReleases(principal, projectId, null, model, response);
        }
        try {
            ReleaseView release = releaseService.createDraft(principal.organizationId(), projectId, request);
            return "redirect:" + releasePath(projectId, release.id());
        } catch (ReleaseVersionTakenException exception) {
            return renderReleasesWithError(principal, projectId, HttpStatus.CONFLICT, exception, model, response);
        } catch (InvalidReleaseScheduleException exception) {
            return renderReleasesWithError(principal, projectId, HttpStatus.BAD_REQUEST, exception, model, response);
        } catch (ProjectNotFoundException exception) {
            return renderReleasesWithError(principal, projectId, HttpStatus.NOT_FOUND, exception, model, response);
        }
    }

    @GetMapping("/projects/{projectId}/releases/{releaseId}")
    String release(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            Model model,
            HttpServletResponse response
    ) {
        return renderRelease(principal, projectId, releaseId, model, response);
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}")
    String edit(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @Valid @ModelAttribute("releaseRequest") ReleaseRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            return renderRelease(principal, projectId, releaseId, model, response);
        }
        return act(principal, projectId, releaseId, model, response,
                () -> releaseService.edit(principal.organizationId(), projectId, releaseId, request));
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/schedule")
    String schedule(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @ModelAttribute ReleaseScheduleRequest request,
            Model model,
            HttpServletResponse response
    ) {
        return act(principal, projectId, releaseId, model, response,
                () -> releaseService.schedule(principal.organizationId(), projectId, releaseId, request));
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/discard")
    String discard(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            Model model,
            HttpServletResponse response
    ) {
        return act(principal, projectId, releaseId, model, response, "/releases?project=" + projectId,
                () -> releaseService.discard(principal.organizationId(), projectId, releaseId));
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/changes")
    String addChanges(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @ModelAttribute ReleaseChangesRequest request,
            Model model,
            HttpServletResponse response
    ) {
        if (!request.isSelection()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("pageError", "Choose changes to add, or add all available changes.");
            return renderRelease(principal, projectId, releaseId, model, response);
        }
        return act(principal, projectId, releaseId, model, response,
                () -> releaseService.addChanges(principal.organizationId(), projectId, releaseId, request));
    }

    // Removes a change from a draft, or rejects it while the release is in review.
    @PostMapping("/projects/{projectId}/releases/{releaseId}/changes/{changeId}/remove")
    String removeChange(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @PathVariable UUID changeId,
            Model model,
            HttpServletResponse response
    ) {
        return act(principal, projectId, releaseId, model, response,
                () -> releaseService.removeChange(principal.organizationId(), projectId, releaseId, changeId));
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/request-review")
    String requestReview(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            Model model,
            HttpServletResponse response
    ) {
        return act(principal, projectId, releaseId, model, response,
                () -> releaseService.requestReview(principal.organizationId(), projectId, releaseId));
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/changes/{changeId}/decision")
    String decide(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @PathVariable UUID changeId,
            @ModelAttribute ReleaseDecisionRequest request,
            Model model,
            HttpServletResponse response
    ) {
        if (request.getBreaking() == null) {
            request.setBreaking(false);
        }
        if (request.getAction() == null || request.getNote() != null && request.getNote().length() > 2000) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("pageError", "Approve or edit the change, with a note of at most 2000 characters.");
            return renderRelease(principal, projectId, releaseId, model, response);
        }
        return act(principal, projectId, releaseId, model, response,
                () -> releaseService.decide(principal, projectId, releaseId, changeId, request));
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/approve")
    String approve(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            Model model,
            HttpServletResponse response
    ) {
        return act(principal, projectId, releaseId, model, response,
                () -> releaseService.approve(principal, projectId, releaseId));
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/return-to-draft")
    String returnToDraft(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            Model model,
            HttpServletResponse response
    ) {
        return act(principal, projectId, releaseId, model, response,
                () -> releaseService.returnToDraft(principal.organizationId(), projectId, releaseId));
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/changes/{changeId}/summary")
    String editSummary(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @PathVariable UUID changeId,
            @Valid @ModelAttribute("summaryRequest") ChangeSummaryRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            return renderInvalid(principal, projectId, releaseId, bindingResult, model, response);
        }
        return act(principal, projectId, releaseId, model, response,
                () -> releaseService.editSummary(principal, projectId, releaseId, changeId, request));
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/notes/{noteId}")
    String editNote(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @PathVariable UUID noteId,
            @Valid @ModelAttribute("noteRequest") ReleaseNoteRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            return renderInvalid(principal, projectId, releaseId, bindingResult, model, response);
        }
        return act(principal, projectId, releaseId, model, response,
                () -> releaseService.editNote(principal, projectId, releaseId, noteId, request));
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/publish")
    String publish(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            Model model,
            HttpServletResponse response
    ) {
        return act(principal, projectId, releaseId, model, response,
                () -> releaseService.publish(principal, projectId, releaseId));
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/translations/retry")
    String retryTranslations(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            Model model,
            HttpServletResponse response
    ) {
        return act(principal, projectId, releaseId, model, response,
                releasePath(projectId, releaseId) + "#release-notes",
                () -> releaseService.retryTranslations(principal.organizationId(), projectId, releaseId));
    }

    private String act(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            UUID releaseId,
            Model model,
            HttpServletResponse response,
            Runnable action
    ) {
        return act(principal, projectId, releaseId, model, response, releasePath(projectId, releaseId), action);
    }

    // Runs one release action and redirects, or renders the release page with the failure.
    private String act(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            UUID releaseId,
            Model model,
            HttpServletResponse response,
            String successPath,
            Runnable action
    ) {
        try {
            action.run();
        } catch (ReleaseNotFoundException exception) {
            return renderRelease(principal, projectId, releaseId, model, response);
        } catch (ChangeNotFoundException | ReleaseNoteNotFoundException exception) {
            return renderReleaseWithError(principal, projectId, releaseId, HttpStatus.NOT_FOUND, exception, model, response);
        } catch (InvalidReleaseScheduleException | InvalidChangeReviewException exception) {
            return renderReleaseWithError(principal, projectId, releaseId, HttpStatus.BAD_REQUEST, exception, model, response);
        } catch (ReleasePublishedException
                 | ReleaseStatusException
                 | ReleaseEmptyException
                 | ReleaseReviewIncompleteException
                 | ReleaseVersionTakenException
                 | ClassificationChangedException
                 | ChangeNotReleasableException
                 | ChangeProcessingException
                 | ReleaseNotesMissingException
                 | TranslationsNotReadyException
                 | ReleaseNoteRenderException exception) {
            return renderReleaseWithError(principal, projectId, releaseId, HttpStatus.CONFLICT, exception, model, response);
        }
        return "redirect:" + successPath;
    }

    private String renderReleases(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            String status,
            Model model,
            HttpServletResponse response
    ) {
        List<ProjectView> projects = projectService.list(principal.organizationId());
        UUID selectedProjectId = projectId;
        if (selectedProjectId == null && !projects.isEmpty()) {
            selectedProjectId = projects.getFirst().id();
        }
        // An unknown status shows every release rather than an error.
        ReleaseStatus selectedStatus = ReleaseStatus.fromValue(status).orElse(null);
        model.addAttribute("projects", projects);
        model.addAttribute("selectedProjectId", selectedProjectId);
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("statuses", ReleaseStatus.values());
        model.addAttribute("releases", List.of());
        model.addAttribute("statusCounts", Map.of());
        model.addAttribute("releaseCount", 0);
        model.addAttribute("projectFound", false);
        if (!model.containsAttribute("releaseRequest")) {
            model.addAttribute("releaseRequest", new NewReleaseRequest());
        }
        if (selectedProjectId != null) {
            try {
                List<ReleaseSummary> releases = releaseService.list(principal.organizationId(), selectedProjectId);
                Map<ReleaseStatus, Long> counts = new EnumMap<>(ReleaseStatus.class);
                for (ReleaseStatus value : ReleaseStatus.values()) {
                    counts.put(value, releases.stream().filter(release -> release.status() == value).count());
                }
                model.addAttribute("releases", releases.stream()
                        .filter(release -> selectedStatus == null || release.status() == selectedStatus)
                        .toList());
                model.addAttribute("statusCounts", counts);
                model.addAttribute("releaseCount", releases.size());
                model.addAttribute("projectFound", true);
            } catch (ProjectNotFoundException exception) {
                response.setStatus(HttpStatus.NOT_FOUND.value());
                model.addAttribute("pageError", messages.of(exception));
            }
        }
        return "releases";
    }

    private String renderReleasesWithError(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            HttpStatus status,
            LocalizedException exception,
            Model model,
            HttpServletResponse response
    ) {
        response.setStatus(status.value());
        model.addAttribute("pageError", messages.of(exception));
        return renderReleases(principal, projectId, null, model, response);
    }

    private String renderRelease(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            UUID releaseId,
            Model model,
            HttpServletResponse response
    ) {
        model.addAttribute("projectId", projectId);
        try {
            ReleaseView release = releaseService.get(principal.organizationId(), projectId, releaseId);
            model.addAttribute("release", release);
            if (release.status() == ReleaseStatus.PUBLISHED) {
                return "release-note";
            }
            model.addAttribute("categories", categoryService.active(principal.organizationId()).stream()
                    .filter(category -> !category.isUnknown())
                    .toList());
            model.addAttribute("audiences", audienceService.list(principal.organizationId()));
            model.addAttribute("language", outputLanguageService.outputLanguage(principal.organizationId()).tag());
            model.addAttribute("notePreviews", notePreviews(principal, projectId, release, model));
            model.addAttribute("availableChanges", release.status() == ReleaseStatus.DRAFT
                    ? releaseService.availableChanges(principal.organizationId(), projectId, releaseId)
                    : List.of());
            if (!model.containsAttribute("releaseRequest")) {
                ReleaseRequest request = new ReleaseRequest();
                request.setVersion(release.version());
                request.setSummary(release.summary());
                model.addAttribute("releaseRequest", request);
            }
        } catch (ReleaseNotFoundException exception) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            model.addAttribute("pageError", messages.of(exception));
        }
        return "release";
    }

    // A template that cannot render only hides the preview; approval reports it as an error.
    private List<AudienceNotePreview> notePreviews(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            ReleaseView release,
            Model model
    ) {
        if (release.status() == ReleaseStatus.APPROVED) {
            return List.of();
        }
        try {
            return releaseService.previewNotes(principal.organizationId(), projectId, release.id());
        } catch (ReleaseNoteRenderException exception) {
            if (!model.containsAttribute("pageError")) {
                model.addAttribute("pageError", messages.of(exception));
            }
            return List.of();
        }
    }

    // A form failed validation: its first message is shown above the page.
    private String renderInvalid(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            UUID releaseId,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        model.addAttribute("pageError", bindingResult.getAllErrors().getFirst().getDefaultMessage());
        return renderRelease(principal, projectId, releaseId, model, response);
    }

    private String renderReleaseWithError(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            UUID releaseId,
            HttpStatus status,
            LocalizedException exception,
            Model model,
            HttpServletResponse response
    ) {
        response.setStatus(status.value());
        model.addAttribute("pageError", messages.of(exception));
        return renderRelease(principal, projectId, releaseId, model, response);
    }

    private static String releasePath(UUID projectId, UUID releaseId) {
        return "/projects/" + projectId + "/releases/" + releaseId;
    }
}
