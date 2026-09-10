package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

@Controller
public class ProjectPageController {

    private final ProjectService projectService;
    private final GitHubIntegrationService integrationService;

    ProjectPageController(ProjectService projectService, GitHubIntegrationService integrationService) {
        this.projectService = projectService;
        this.integrationService = integrationService;
    }

    @GetMapping("/projects")
    String projects(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            Model model
    ) {
        addPageModel(principal, model);
        return "projects";
    }

    @PostMapping("/projects")
    String createProject(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @Valid @ModelAttribute("projectRequest") ProjectRequest request,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            addPageModel(principal, model);
            return "projects";
        }
        projectService.create(principal.organizationId(), request);
        return "redirect:/projects";
    }

    @PostMapping("/projects/{projectId}/github-integration")
    String configureGitHub(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @ModelAttribute("githubIntegrationRequest") GitHubIntegrationRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("integrationProjectId", projectId);
            addPageModel(principal, model);
            return "projects";
        }

        try {
            GitHubIntegrationCreated created = integrationService.configure(
                    principal.organizationId(),
                    projectId,
                    request
            );
            response.setHeader("Cache-Control", CacheControl.noStore().getHeaderValue());
            model.addAttribute("integration", created);
            return "github-integration-created";
        } catch (ProjectNotFoundException exception) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            bindingResult.reject("project.notFound", exception.getMessage());
            model.addAttribute("integrationError", exception.getMessage());
        } catch (GitHubIntegrationAlreadyConfiguredException | GitHubRepositoryAlreadyConnectedException exception) {
            response.setStatus(HttpStatus.CONFLICT.value());
            bindingResult.reject("githubIntegration.conflict", exception.getMessage());
            model.addAttribute("integrationError", exception.getMessage());
        }

        model.addAttribute("integrationProjectId", projectId);
        addPageModel(principal, model);
        return "projects";
    }

    private void addPageModel(ReleaseFlowPrincipal principal, Model model) {
        if (!model.containsAttribute("projectRequest")) {
            model.addAttribute("projectRequest", new ProjectRequest());
        }
        if (!model.containsAttribute("githubIntegrationRequest")) {
            model.addAttribute("githubIntegrationRequest", new GitHubIntegrationRequest());
        }
        model.addAttribute("projects", projectService.list(principal.organizationId()));
    }
}
