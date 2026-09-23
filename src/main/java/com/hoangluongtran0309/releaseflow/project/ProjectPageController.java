package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.account.InvalidOutputLanguageException;
import com.hoangluongtran0309.releaseflow.account.OutputLanguageRequest;
import com.hoangluongtran0309.releaseflow.account.InvalidOrganizationSlugException;
import com.hoangluongtran0309.releaseflow.account.OrganizationSlugRequest;
import com.hoangluongtran0309.releaseflow.account.OrganizationSlugService;
import com.hoangluongtran0309.releaseflow.account.OrganizationSlugTakenException;
import com.hoangluongtran0309.releaseflow.account.OutputLanguageService;
import com.hoangluongtran0309.releaseflow.changelog.PublicChangelogUrls;
import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.configuration.UiMessages;
import com.hoangluongtran0309.releaseflow.gitlab.GitLabHostNotAllowedException;
import com.hoangluongtran0309.releaseflow.gitlab.InvalidGitLabBaseUrlException;
import com.hoangluongtran0309.releaseflow.jira.InvalidJiraSiteException;
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
    private final IntegrationSourceService sourceService;
    private final OutputLanguageService outputLanguageService;
    private final OrganizationSlugService slugService;
    private final PublicChangelogUrls changelogUrls;
    private final UiMessages messages;

    ProjectPageController(
            ProjectService projectService,
            IntegrationSourceService sourceService,
            OutputLanguageService outputLanguageService,
            OrganizationSlugService slugService,
            PublicChangelogUrls changelogUrls,
            UiMessages messages
    ) {
        this.projectService = projectService;
        this.sourceService = sourceService;
        this.outputLanguageService = outputLanguageService;
        this.slugService = slugService;
        this.changelogUrls = changelogUrls;
        this.messages = messages;
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

    @PostMapping("/projects/{projectId}/sources")
    String createSource(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @ModelAttribute("sourceRequest") IntegrationSourceRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("sourceProjectId", projectId);
            addPageModel(principal, model);
            return "projects";
        }

        try {
            IntegrationSourceCreated created = sourceService.create(principal.organizationId(), projectId, request);
            response.setHeader("Cache-Control", CacheControl.noStore().getHeaderValue());
            model.addAttribute("source", created);
            return "source-created";
        } catch (ProjectNotFoundException exception) {
            // The project is not in this tenant's list, so there is no card to attach the error to.
            response.setStatus(HttpStatus.NOT_FOUND.value());
            bindingResult.reject("project.notFound", messages.of(exception));
            model.addAttribute("pageError", messages.of(exception));
        } catch (SourceAlreadyConnectedException exception) {
            response.setStatus(HttpStatus.CONFLICT.value());
            bindingResult.reject("source.conflict", messages.of(exception));
            model.addAttribute("sourceError", messages.of(exception));
        } catch (InvalidGitLabBaseUrlException | GitLabHostNotAllowedException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            bindingResult.rejectValue("apiBaseUrl", "source.apiBaseUrl.invalid", messages.of(exception));
            model.addAttribute("sourceError", messages.of(exception));
        } catch (InvalidJiraSiteException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            bindingResult.rejectValue("siteUrl", "source.siteUrl.invalid", messages.of(exception));
            model.addAttribute("sourceError", messages.of(exception));
        } catch (SourceTokenRejectedException exception) {
            // The provider was asked before anything was written, so nothing was stored.
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            bindingResult.rejectValue("apiToken", "source.apiToken.rejected", messages.of(exception));
            model.addAttribute("sourceError", messages.of(exception));
        } catch (SourceUnavailableException exception) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            model.addAttribute("sourceError", messages.of(exception));
        }

        model.addAttribute("sourceProjectId", projectId);
        addPageModel(principal, model);
        return "projects";
    }

    @PostMapping("/projects/{projectId}/sources/{sourceId}/token")
    String replaceToken(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID sourceId,
            @Valid @ModelAttribute("sourceTokenRequest") SourceTokenRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        if (!bindingResult.hasErrors()) {
            try {
                sourceService.replaceToken(principal.organizationId(), projectId, sourceId, request);
                return "redirect:/projects?tokenSaved";
            } catch (ProjectNotFoundException | SourceNotFoundException exception) {
                response.setStatus(HttpStatus.NOT_FOUND.value());
                model.addAttribute("pageError", messages.of(exception));
            } catch (SourceTokenRejectedException exception) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                model.addAttribute("tokenError", messages.of(exception));
            } catch (SourceUnavailableException exception) {
                response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
                model.addAttribute("tokenError", messages.of(exception));
            }
        }
        // The submitted token is never rendered back into the page.
        model.addAttribute("tokenSourceId", sourceId);
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
                bindingResult.rejectValue("outputLanguage", "outputLanguage.invalid", messages.of(exception));
            }
        }
        addPageModel(principal, model);
        return "projects";
    }

    /**
     * The address the Organization's public changelog answers on. Changing it moves the
     * changelog, so the page says as much and only administrators reach this path.
     */
    @PostMapping("/organization/slug")
    String changeSlug(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @Valid @ModelAttribute("organizationSlugRequest") OrganizationSlugRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        if (!bindingResult.hasErrors()) {
            try {
                slugService.change(principal.organizationId(), request.getSlug());
                return "redirect:/projects?changelogSaved";
            } catch (InvalidOrganizationSlugException exception) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                bindingResult.rejectValue("slug", "slug.invalid", messages.of(exception));
            } catch (OrganizationSlugTakenException exception) {
                response.setStatus(HttpStatus.CONFLICT.value());
                bindingResult.rejectValue("slug", "slug.taken", messages.of(exception));
            }
        }
        addPageModel(principal, model);
        return "projects";
    }

    private void addPageModel(ReleaseFlowPrincipal principal, Model model) {
        if (!model.containsAttribute("projectRequest")) {
            model.addAttribute("projectRequest", new ProjectRequest());
        }
        if (!model.containsAttribute("sourceRequest")) {
            model.addAttribute("sourceRequest", new IntegrationSourceRequest());
        }
        if (!model.containsAttribute("sourceTokenRequest")) {
            model.addAttribute("sourceTokenRequest", new SourceTokenRequest());
        }
        model.addAttribute("outputLanguage", outputLanguageService.settings(principal.organizationId()));
        if (!model.containsAttribute("outputLanguageRequest")) {
            OutputLanguageRequest languageRequest = new OutputLanguageRequest();
            languageRequest.setOutputLanguage(outputLanguageService.outputLanguage(principal.organizationId()).tag());
            model.addAttribute("outputLanguageRequest", languageRequest);
        }
        String slug = slugService.slug(principal.organizationId()).value();
        model.addAttribute("changelogSlug", slug);
        model.addAttribute("changelogUrl", changelogUrls.rootUrl(slug));
        if (!model.containsAttribute("organizationSlugRequest")) {
            OrganizationSlugRequest slugRequest = new OrganizationSlugRequest();
            slugRequest.setSlug(slug);
            model.addAttribute("organizationSlugRequest", slugRequest);
        }
        model.addAttribute("projects", projectService.list(principal.organizationId()));
    }
}
