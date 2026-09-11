package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.change.ChangeNotFoundException;
import com.hoangluongtran0309.releaseflow.project.ProjectNotFoundException;
import com.hoangluongtran0309.releaseflow.project.ProjectService;
import com.hoangluongtran0309.releaseflow.project.ProjectView;
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

import java.util.List;
import java.util.UUID;

@Controller
public class ReleasePageController {

    private final ProjectService projectService;
    private final ReleaseService releaseService;

    ReleasePageController(ProjectService projectService, ReleaseService releaseService) {
        this.projectService = projectService;
        this.releaseService = releaseService;
    }

    @GetMapping("/releases")
    String releases(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @RequestParam(name = "project", required = false) UUID projectId,
            Model model,
            HttpServletResponse response
    ) {
        return renderReleases(principal, projectId, model, response);
    }

    @PostMapping("/projects/{projectId}/releases")
    String create(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @ModelAttribute("releaseRequest") ReleaseRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            return renderReleases(principal, projectId, model, response);
        }
        try {
            ReleaseView draft = releaseService.createDraft(principal.organizationId(), projectId, request);
            return "redirect:" + draftPath(projectId, draft.id());
        } catch (DraftReleaseExistsException exception) {
            return renderReleasesWithError(principal, projectId, HttpStatus.CONFLICT, exception, model, response);
        } catch (ProjectNotFoundException exception) {
            return renderReleasesWithError(principal, projectId, HttpStatus.NOT_FOUND, exception, model, response);
        }
    }

    @GetMapping("/projects/{projectId}/releases/{releaseId}")
    String draft(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            Model model,
            HttpServletResponse response
    ) {
        return renderDraft(principal, projectId, releaseId, model, response);
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
            return renderDraft(principal, projectId, releaseId, model, response);
        }
        try {
            releaseService.edit(principal.organizationId(), projectId, releaseId, request);
        } catch (ReleaseNotFoundException exception) {
            return renderDraft(principal, projectId, releaseId, model, response);
        }
        return "redirect:" + draftPath(projectId, releaseId);
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/discard")
    String discard(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            Model model,
            HttpServletResponse response
    ) {
        try {
            releaseService.discard(principal.organizationId(), projectId, releaseId);
        } catch (ReleaseNotFoundException exception) {
            return renderDraft(principal, projectId, releaseId, model, response);
        }
        return "redirect:/releases?project=" + projectId;
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
        try {
            if (!request.isSelection()) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                model.addAttribute("pageError", "Choose changes to add, or add all available changes.");
                return renderDraft(principal, projectId, releaseId, model, response);
            }
            releaseService.addChanges(principal.organizationId(), projectId, releaseId, request);
        } catch (ChangeNotFoundException exception) {
            return renderDraftWithError(principal, projectId, releaseId, HttpStatus.NOT_FOUND, exception, model, response);
        } catch (ChangeNotReleasableException exception) {
            return renderDraftWithError(principal, projectId, releaseId, HttpStatus.CONFLICT, exception, model, response);
        } catch (ReleaseNotFoundException exception) {
            return renderDraft(principal, projectId, releaseId, model, response);
        }
        return "redirect:" + draftPath(projectId, releaseId);
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/changes/{changeId}/remove")
    String removeChange(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @PathVariable UUID changeId,
            Model model,
            HttpServletResponse response
    ) {
        try {
            releaseService.removeChange(principal.organizationId(), projectId, releaseId, changeId);
        } catch (ChangeNotFoundException exception) {
            return renderDraftWithError(principal, projectId, releaseId, HttpStatus.NOT_FOUND, exception, model, response);
        } catch (ReleaseNotFoundException exception) {
            return renderDraft(principal, projectId, releaseId, model, response);
        }
        return "redirect:" + draftPath(projectId, releaseId);
    }

    private String renderReleases(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            Model model,
            HttpServletResponse response
    ) {
        List<ProjectView> projects = projectService.list(principal.organizationId());
        UUID selectedProjectId = projectId;
        if (selectedProjectId == null && !projects.isEmpty()) {
            selectedProjectId = projects.getFirst().id();
        }
        model.addAttribute("projects", projects);
        model.addAttribute("selectedProjectId", selectedProjectId);
        model.addAttribute("releases", List.of());
        model.addAttribute("projectFound", false);
        if (!model.containsAttribute("releaseRequest")) {
            model.addAttribute("releaseRequest", new ReleaseRequest());
        }
        if (selectedProjectId != null) {
            try {
                model.addAttribute("releases", releaseService.list(principal.organizationId(), selectedProjectId));
                model.addAttribute("projectFound", true);
            } catch (ProjectNotFoundException exception) {
                response.setStatus(HttpStatus.NOT_FOUND.value());
                model.addAttribute("pageError", exception.getMessage());
            }
        }
        return "releases";
    }

    private String renderReleasesWithError(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            HttpStatus status,
            RuntimeException exception,
            Model model,
            HttpServletResponse response
    ) {
        response.setStatus(status.value());
        model.addAttribute("pageError", exception.getMessage());
        return renderReleases(principal, projectId, model, response);
    }

    private String renderDraft(
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
            model.addAttribute(
                    "availableChanges",
                    releaseService.availableChanges(principal.organizationId(), projectId, releaseId)
            );
            if (!model.containsAttribute("releaseRequest")) {
                ReleaseRequest request = new ReleaseRequest();
                request.setVersion(release.version());
                request.setSummary(release.summary());
                model.addAttribute("releaseRequest", request);
            }
        } catch (ReleaseNotFoundException exception) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            model.addAttribute("pageError", exception.getMessage());
        }
        return "release-draft";
    }

    private String renderDraftWithError(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            UUID releaseId,
            HttpStatus status,
            RuntimeException exception,
            Model model,
            HttpServletResponse response
    ) {
        response.setStatus(status.value());
        model.addAttribute("pageError", exception.getMessage());
        return renderDraft(principal, projectId, releaseId, model, response);
    }

    private static String draftPath(UUID projectId, UUID releaseId) {
        return "/projects/" + projectId + "/releases/" + releaseId;
    }
}
