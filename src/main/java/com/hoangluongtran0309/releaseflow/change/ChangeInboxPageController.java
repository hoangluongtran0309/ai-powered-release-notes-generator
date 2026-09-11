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
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@Controller
public class ChangeInboxPageController {

    private final ProjectService projectService;
    private final ChangeInboxService inboxService;

    ChangeInboxPageController(ProjectService projectService, ChangeInboxService inboxService) {
        this.projectService = projectService;
        this.inboxService = inboxService;
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
