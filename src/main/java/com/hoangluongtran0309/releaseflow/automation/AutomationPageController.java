package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.audience.AudienceService;
import com.hoangluongtran0309.releaseflow.audience.ReleaseLanguageService;
import com.hoangluongtran0309.releaseflow.project.ProjectService;
import com.hoangluongtran0309.releaseflow.release.ReleaseAccess;
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

import java.util.UUID;
import java.util.function.Supplier;

// Administrator only, by URL rule.
@Controller
public class AutomationPageController {

    private static final String FORM = "ruleForm";
    private static final int BLANK_ACTION_ROWS = 3;

    private final AutomationRuleService ruleService;
    private final AutomationRunService runService;
    private final AudienceService audienceService;
    private final ReleaseLanguageService releaseLanguageService;
    private final ProjectService projectService;
    private final ReleaseAccess releaseAccess;

    AutomationPageController(
            AutomationRuleService ruleService,
            AutomationRunService runService,
            AudienceService audienceService,
            ReleaseLanguageService releaseLanguageService,
            ProjectService projectService,
            ReleaseAccess releaseAccess
    ) {
        this.ruleService = ruleService;
        this.runService = runService;
        this.audienceService = audienceService;
        this.releaseLanguageService = releaseLanguageService;
        this.projectService = projectService;
        this.releaseAccess = releaseAccess;
    }

    @GetMapping("/automation")
    String automation(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        return render(principal, null, page, model);
    }

    @GetMapping("/automation/new")
    String newRule(@AuthenticationPrincipal ReleaseFlowPrincipal principal, Model model) {
        return render(principal, null, 0, model);
    }

    @GetMapping("/automation/{ruleId}")
    String rule(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID ruleId,
            Model model,
            HttpServletResponse response
    ) {
        try {
            return render(principal, ruleService.get(principal.organizationId(), ruleId), 0, model);
        } catch (AutomationRuleNotFoundException exception) {
            return renderWithError(principal, HttpStatus.NOT_FOUND, exception.getMessage(), model, response);
        }
    }

    @PostMapping("/automation")
    String create(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @Valid @ModelAttribute(FORM) AutomationRuleRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return render(principal, null, 0, model);
        }
        return act(principal, model, response, "/automation?saved",
                () -> ruleService.create(principal.organizationId(), request));
    }

    @PostMapping("/automation/{ruleId}")
    String update(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID ruleId,
            @Valid @ModelAttribute(FORM) AutomationRuleRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return render(principal, null, 0, model);
        }
        return act(principal, model, response, "/automation?saved",
                () -> ruleService.update(principal.organizationId(), ruleId, request));
    }

    @PostMapping("/automation/{ruleId}/enable")
    String enable(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID ruleId,
            Model model,
            HttpServletResponse response
    ) {
        return act(principal, model, response, "/automation?enabled",
                () -> ruleService.enable(principal.organizationId(), ruleId));
    }

    @PostMapping("/automation/{ruleId}/disable")
    String disable(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID ruleId,
            Model model,
            HttpServletResponse response
    ) {
        return act(principal, model, response, "/automation?disabled",
                () -> ruleService.disable(principal.organizationId(), ruleId));
    }

    @PostMapping("/automation/{ruleId}/archive")
    String archive(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID ruleId,
            Model model,
            HttpServletResponse response
    ) {
        return act(principal, model, response, "/automation?archived", () -> {
            ruleService.archive(principal.organizationId(), ruleId);
            return null;
        });
    }

    /**
     * Runs a rule by hand. The request ID travels with the form, so submitting the same
     * page twice is the same run rather than a second delivery.
     */
    @PostMapping("/automation/{ruleId}/execute")
    String execute(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID ruleId,
            @Valid @ModelAttribute ExecuteRuleRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            return renderWithError(principal, HttpStatus.BAD_REQUEST, "Choose a published release.", model, response);
        }
        return act(principal, model, response, "/automation?executed",
                () -> runService.execute(principal, ruleId, request));
    }

    @PostMapping("/automation/runs/{runId}/retry")
    String retry(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID runId,
            @ModelAttribute RetryRunRequest request,
            Model model,
            HttpServletResponse response
    ) {
        return act(principal, model, response, "/automation?retried",
                () -> runService.retry(principal.organizationId(), runId, request.isConfirmUnknown()));
    }

    @PostMapping("/automation/runs/{runId}/cancel")
    String cancel(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID runId,
            Model model,
            HttpServletResponse response
    ) {
        return act(principal, model, response, "/automation?cancelled",
                () -> runService.cancel(principal.organizationId(), runId));
    }

    private String act(
            ReleaseFlowPrincipal principal,
            Model model,
            HttpServletResponse response,
            String successPath,
            Supplier<?> action
    ) {
        try {
            action.get();
        } catch (AutomationRuleNotFoundException | AutomationRunNotFoundException exception) {
            return renderWithError(principal, HttpStatus.NOT_FOUND, exception.getMessage(), model, response);
        } catch (AutomationConflictException exception) {
            return renderWithError(principal, HttpStatus.CONFLICT, exception.getMessage(), model, response);
        } catch (AutomationActionInvalidException exception) {
            return renderWithError(principal, HttpStatus.BAD_REQUEST, exception.getMessage(), model, response);
        }
        return "redirect:" + successPath;
    }

    private String render(ReleaseFlowPrincipal principal, AutomationRuleView editing, int page, Model model) {
        UUID organizationId = principal.organizationId();
        model.addAttribute("rules", ruleService.list(organizationId));
        model.addAttribute("runs", runService.list(organizationId, Math.max(0, page), AutomationRunPage.DEFAULT_SIZE));
        model.addAttribute("audiences", audienceService.list(organizationId));
        model.addAttribute("languages", releaseLanguageService.targetLanguages(organizationId));
        model.addAttribute("projects", projectService.list(organizationId));
        model.addAttribute("releases", releaseAccess.published(organizationId));
        model.addAttribute("triggers", TriggerType.values());
        model.addAttribute("actionTypes", ActionType.values());
        model.addAttribute("editing", editing);
        model.addAttribute("requestId", UUID.randomUUID());
        if (!model.containsAttribute(FORM)) {
            model.addAttribute(FORM, form(editing));
        }
        return "automation";
    }

    // The form always offers a few empty rows, so an action can be added without a script.
    private AutomationRuleRequest form(AutomationRuleView editing) {
        AutomationRuleRequest request = new AutomationRuleRequest();
        if (editing != null) {
            request.setName(editing.name());
            request.setTriggerType(editing.triggerType());
            request.setProjectId(editing.projectId());
            editing.actions().forEach(action -> {
                AutomationActionRequest row = new AutomationActionRequest();
                row.setId(action.id());
                row.setActionType(action.actionType());
                row.setAudienceId(action.audienceId());
                row.setLanguage(action.language());
                row.setRecipients(action.recipients());
                request.getActions().add(row);
            });
        }
        for (int row = 0; row < BLANK_ACTION_ROWS; row++) {
            request.getActions().add(new AutomationActionRequest());
        }
        return request;
    }

    private String renderWithError(
            ReleaseFlowPrincipal principal,
            HttpStatus status,
            String message,
            Model model,
            HttpServletResponse response
    ) {
        response.setStatus(status.value());
        model.addAttribute("pageError", message);
        return render(principal, null, 0, model);
    }
}
