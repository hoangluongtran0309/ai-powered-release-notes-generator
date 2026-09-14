package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.account.InvalidOutputLanguageException;
import com.hoangluongtran0309.releaseflow.account.OutputLanguageRequest;
import com.hoangluongtran0309.releaseflow.account.OutputLanguageService;
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
    private final OutputLanguageService outputLanguageService;

    ProjectPageController(
            ProjectService projectService,
            GitHubIntegrationService integrationService,
            OutputLanguageService outputLanguageService
    ) {
        this.projectService = projectService;
        this.integrationService = integrationService;
        this.outputLanguageService = outputLanguageService;
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
            // The project is not in this tenant's list, so there is no card to attach the error to.
            response.setStatus(HttpStatus.NOT_FOUND.value());
            bindingResult.reject("project.notFound", exception.getMessage());
            model.addAttribute("pageError", exception.getMessage());
        } catch (GitHubIntegrationAlreadyConfiguredException | GitHubRepositoryAlreadyConnectedException exception) {
            response.setStatus(HttpStatus.CONFLICT.value());
            bindingResult.reject("githubIntegration.conflict", exception.getMessage());
            model.addAttribute("integrationError", exception.getMessage());
        }

        model.addAttribute("integrationProjectId", projectId);
        addPageModel(principal, model);
        return "projects";
    }

    @PostMapping("/projects/{projectId}/github-integration/token")
    String replaceGitHubToken(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @ModelAttribute("githubTokenRequest") GitHubTokenRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        if (!bindingResult.hasErrors()) {
            try {
                integrationService.replaceToken(principal.organizationId(), projectId, request);
                return "redirect:/projects?tokenSaved";
            } catch (ProjectNotFoundException | GitHubIntegrationNotFoundException exception) {
                response.setStatus(HttpStatus.NOT_FOUND.value());
                model.addAttribute("pageError", exception.getMessage());
            } catch (GitHubTokenRejectedException exception) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                model.addAttribute("tokenError", exception.getMessage());
            } catch (GitHubUnavailableException exception) {
                response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
                model.addAttribute("tokenError", exception.getMessage());
            }
        }
        // The submitted token is never rendered back into the page.
        model.addAttribute("tokenProjectId", projectId);
        addPageModel(principal, model);
        return "projects";
    }

    // The Organization's output language is shown with its Projects; administrators change it here.
    @PostMapping("/organization/output-language")
    String changeOutputLanguage(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @Valid @ModelAttribute("outputLanguageRequest") OutputLanguageRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        if (!bindingResult.hasErrors()) {
            try {
                outputLanguageService.change(principal.organizationId(), request.getOutputLanguage());
                return "redirect:/projects?languageSaved";
            } catch (InvalidOutputLanguageException exception) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                bindingResult.rejectValue("outputLanguage", "outputLanguage.invalid", exception.getMessage());
            }
        }
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
        if (!model.containsAttribute("githubTokenRequest")) {
            model.addAttribute("githubTokenRequest", new GitHubTokenRequest());
        }
        model.addAttribute("outputLanguage", outputLanguageService.settings(principal.organizationId()));
        if (!model.containsAttribute("outputLanguageRequest")) {
            OutputLanguageRequest languageRequest = new OutputLanguageRequest();
            languageRequest.setOutputLanguage(outputLanguageService.outputLanguage(principal.organizationId()).tag());
            model.addAttribute("outputLanguageRequest", languageRequest);
        }
        model.addAttribute("projects", projectService.list(principal.organizationId()));
    }
}
