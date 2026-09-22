package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.configuration.UiMessages;
import com.hoangluongtran0309.releaseflow.project.ProjectNotFoundException;
import com.hoangluongtran0309.releaseflow.project.ProjectService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;
import java.util.UUID;

// Members see a Project's sensitive paths; saving is administrator only, by URL rule.
@Controller
public class SensitivePathPageController {

    private final ProjectService projectService;
    private final ProjectSensitivePathService sensitivePathService;
    private final UiMessages messages;

    SensitivePathPageController(
            ProjectService projectService,
            ProjectSensitivePathService sensitivePathService,
            UiMessages messages
    ) {
        this.projectService = projectService;
        this.sensitivePathService = sensitivePathService;
        this.messages = messages;
    }

    @GetMapping("/projects/{projectId}/sensitive-paths")
    String sensitivePaths(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            Model model,
            HttpServletResponse response
    ) {
        return render(principal, projectId, null, model, response);
    }

    // One pattern per line; blank lines and repeats are dropped.
    @PostMapping("/projects/{projectId}/sensitive-paths")
    String save(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @RequestParam(name = "additions", defaultValue = "") String additions,
            Model model,
            HttpServletResponse response
    ) {
        SensitivePathsRequest request = new SensitivePathsRequest();
        request.setAdditions(Arrays.asList(additions.split("\\R")));
        try {
            sensitivePathService.replace(principal, projectId, request);
            return "redirect:/projects/" + projectId + "/sensitive-paths?saved";
        } catch (InvalidSensitivePathsException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("additionsError", messages.of(exception));
            return render(principal, projectId, additions, model, response);
        } catch (ProjectNotFoundException exception) {
            return notFound(exception, model, response);
        }
    }

    private String render(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            String submitted,
            Model model,
            HttpServletResponse response
    ) {
        try {
            model.addAttribute("project", projectService.get(principal.organizationId(), projectId));
            SensitivePathsView paths = sensitivePathService.view(principal.organizationId(), projectId);
            model.addAttribute("paths", paths);
            model.addAttribute("additionsText", submitted != null ? submitted : String.join("\n", paths.additions()));
            return "sensitive-paths";
        } catch (ProjectNotFoundException exception) {
            return notFound(exception, model, response);
        }
    }

    private String notFound(ProjectNotFoundException exception, Model model, HttpServletResponse response) {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        model.addAttribute("pageError", messages.of(exception));
        return "sensitive-paths";
    }
}
