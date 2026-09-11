package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.project.ProjectNotFoundException;
import com.hoangluongtran0309.releaseflow.project.ProjectService;
import com.hoangluongtran0309.releaseflow.project.ProjectView;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
public class ChangeInboxPageController {

    private final ProjectService projectService;
    private final ChangeInboxService inboxService;
    private final ChangeAiClassificationService aiClassificationService;
    private final ChangeReviewService reviewService;

    ChangeInboxPageController(
            ProjectService projectService,
            ChangeInboxService inboxService,
            ChangeAiClassificationService aiClassificationService,
            ChangeReviewService reviewService
    ) {
        this.projectService = projectService;
        this.inboxService = inboxService;
        this.aiClassificationService = aiClassificationService;
        this.reviewService = reviewService;
    }

    @GetMapping("/changes")
    String changes(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @RequestParam(name = "project", required = false) UUID projectId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            Model model,
            HttpServletResponse response
    ) {
        return renderInbox(principal, projectId, category, status, model, response);
    }

    // Both card actions carry the inbox filters as returnCategory/returnStatus so the
    // reviewer lands back on the same card afterwards.
    @PostMapping("/projects/{projectId}/changes/{changeId}/review")
    String review(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID changeId,
            @ModelAttribute ChangeReviewRequest request,
            @RequestParam(required = false) String returnCategory,
            @RequestParam(required = false) String returnStatus,
            Model model,
            HttpServletResponse response
    ) {
        try {
            if (request.getBreaking() == null) {
                request.setBreaking(false);
            }
            reviewService.review(principal, projectId, changeId, request);
        } catch (ChangeNotFoundException exception) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            model.addAttribute("pageError", exception.getMessage());
            return renderInbox(principal, projectId, returnCategory, returnStatus, model, response);
        } catch (InvalidChangeReviewException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("pageError", exception.getMessage());
            return renderInbox(principal, projectId, returnCategory, returnStatus, model, response);
        }
        return redirectToCard(projectId, changeId, returnCategory, returnStatus);
    }

    @PostMapping("/projects/{projectId}/changes/{changeId}/ai-classification")
    String classifyWithAi(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID changeId,
            @RequestParam(required = false) String returnCategory,
            @RequestParam(required = false) String returnStatus,
            Model model,
            HttpServletResponse response
    ) {
        try {
            aiClassificationService.classify(principal.organizationId(), projectId, changeId);
        } catch (AiClassificationFailedException | ChangeNotEligibleForAiException exception) {
            // The card shows the recorded failure, or the classification that already exists.
        } catch (ChangeNotFoundException exception) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            model.addAttribute("pageError", exception.getMessage());
            return renderInbox(principal, projectId, returnCategory, returnStatus, model, response);
        } catch (AiClassificationUnavailableException exception) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            model.addAttribute("pageError", exception.getMessage());
            return renderInbox(principal, projectId, returnCategory, returnStatus, model, response);
        }
        return redirectToCard(projectId, changeId, returnCategory, returnStatus);
    }

    private static String redirectToCard(UUID projectId, UUID changeId, String category, String status) {
        return "redirect:" + UriComponentsBuilder.fromPath("/changes")
                .queryParam("project", projectId)
                .queryParamIfPresent("category", Optional.ofNullable(category).filter(value -> !value.isBlank()))
                .queryParamIfPresent("status", Optional.ofNullable(status).filter(value -> !value.isBlank()))
                .fragment("change-" + changeId)
                .encode()
                .toUriString();
    }

    private String renderInbox(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            String category,
            String status,
            Model model,
            HttpServletResponse response
    ) {
        List<ProjectView> projects = projectService.list(principal.organizationId());
        UUID selectedProjectId = projectId;
        if (selectedProjectId == null && !projects.isEmpty()) {
            selectedProjectId = projects.getFirst().id();
        }
        model.addAttribute("projects", projects);
        model.addAttribute("categories", ChangeCategory.values());
        model.addAttribute("statuses", ReviewStatus.values());
        model.addAttribute("selectedProjectId", selectedProjectId);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("filtered", hasText(category) || hasText(status));
        model.addAttribute("aiEnabled", aiClassificationService.isEnabled());
        model.addAttribute("changes", List.of());
        if (selectedProjectId == null) {
            return "changes";
        }

        try {
            model.addAttribute("changes", inboxService.list(
                    principal.organizationId(),
                    selectedProjectId,
                    ChangeFilter.parse(category, status)
            ));
        } catch (ProjectNotFoundException exception) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            model.addAttribute("pageError", exception.getMessage());
        } catch (InvalidChangeFilterException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("pageError", exception.getMessage());
        }
        return "changes";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
