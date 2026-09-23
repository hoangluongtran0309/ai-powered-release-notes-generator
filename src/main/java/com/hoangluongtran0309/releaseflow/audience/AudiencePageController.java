package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;
import com.hoangluongtran0309.releaseflow.configuration.UiMessages;
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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

// Administrator only, by URL rule.
@Controller
public class AudiencePageController {

    private static final String FORM = "audienceForm";

    private final AudienceService audienceService;
    private final ReleaseLanguageService releaseLanguageService;
    private final UiMessages messages;

    AudiencePageController(AudienceService audienceService, ReleaseLanguageService releaseLanguageService, UiMessages messages) {
        this.audienceService = audienceService;
        this.releaseLanguageService = releaseLanguageService;
        this.messages = messages;
    }

    @GetMapping("/audiences")
    String audiences(@AuthenticationPrincipal ReleaseFlowPrincipal principal, Model model, HttpServletResponse response) {
        List<AudienceView> audiences = audienceService.list(principal.organizationId());
        return render(principal, audiences.isEmpty() ? null : audiences.getFirst().id(), model, response);
    }

    @GetMapping("/audiences/new")
    String newAudience(@AuthenticationPrincipal ReleaseFlowPrincipal principal, Model model, HttpServletResponse response) {
        return render(principal, null, model, response);
    }

    @GetMapping("/audiences/{audienceId}")
    String audience(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID audienceId,
            Model model,
            HttpServletResponse response
    ) {
        return render(principal, audienceId, model, response);
    }

    @PostMapping("/audiences")
    String create(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @Valid @ModelAttribute(FORM) NewAudienceRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return render(principal, null, model, response);
        }
        try {
            AudienceView audience = audienceService.create(principal.organizationId(), request);
            return "redirect:/audiences/" + audience.id() + "?saved";
        } catch (InvalidAudienceTemplateException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            bindingResult.rejectValue("templateBody", exception.code(), messages.of(exception));
        } catch (AudienceConflictException exception) {
            response.setStatus(HttpStatus.CONFLICT.value());
            if ("audience_code_taken".equals(exception.code())) {
                bindingResult.rejectValue("code", exception.code(), messages.of(exception));
            } else {
                model.addAttribute("pageError", messages.of(exception));
            }
        }
        return render(principal, null, model, response);
    }

    @PostMapping("/audiences/{audienceId}")
    String update(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID audienceId,
            @Valid @ModelAttribute(FORM) AudienceRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return render(principal, audienceId, model, response);
        }
        try {
            audienceService.update(principal.organizationId(), audienceId, request);
            return "redirect:/audiences/" + audienceId + "?saved";
        } catch (InvalidAudienceTemplateException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            bindingResult.rejectValue("templateBody", exception.code(), messages.of(exception));
        } catch (AudienceNotFoundException exception) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            model.addAttribute("pageError", messages.of(exception));
        }
        return render(principal, audienceId, model, response);
    }

    @PostMapping("/audiences/{audienceId}/delete")
    String delete(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID audienceId,
            Model model,
            HttpServletResponse response
    ) {
        try {
            audienceService.delete(principal.organizationId(), audienceId);
            return "redirect:/audiences?deleted";
        } catch (AudienceNotFoundException exception) {
            return renderWithError(principal, audienceId, HttpStatus.NOT_FOUND, exception, model, response);
        } catch (AudienceConflictException exception) {
            return renderWithError(principal, audienceId, HttpStatus.CONFLICT, exception, model, response);
        }
    }

    @PostMapping("/audiences/{audienceId}/reset-to-preset")
    String resetToPreset(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID audienceId,
            Model model,
            HttpServletResponse response
    ) {
        try {
            audienceService.resetToPreset(principal.organizationId(), audienceId);
            return "redirect:/audiences/" + audienceId + "?reset";
        } catch (AudienceNotFoundException exception) {
            return renderWithError(principal, audienceId, HttpStatus.NOT_FOUND, exception, model, response);
        } catch (AudienceConflictException exception) {
            return renderWithError(principal, audienceId, HttpStatus.CONFLICT, exception, model, response);
        }
    }

    /** Saves an audience's template for one release note language. */
    @PostMapping("/audiences/{audienceId}/templates/{language}")
    String updateTemplate(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID audienceId,
            @PathVariable String language,
            @ModelAttribute AudienceTemplateRequest request,
            Model model,
            HttpServletResponse response
    ) {
        try {
            audienceService.updateTemplate(principal.organizationId(), audienceId, language, request.getTemplateBody());
            return "redirect:/audiences/" + audienceId + "?templateSaved#language-templates";
        } catch (InvalidAudienceTemplateException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("variantLanguage", language);
            model.addAttribute("variantBody", request.getTemplateBody());
            model.addAttribute("variantError", messages.of(exception));
        } catch (AudienceNotFoundException exception) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            model.addAttribute("pageError", messages.of(exception));
        }
        return render(principal, audienceId, model, response);
    }

    /** Saves the release note languages, entered as tags separated by commas or spaces. */
    @PostMapping("/audiences/release-languages")
    String replaceReleaseLanguages(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @RequestParam(name = "targetLanguages", defaultValue = "") String targetLanguages,
            Model model,
            HttpServletResponse response
    ) {
        ReleaseLanguagesRequest request = new ReleaseLanguagesRequest();
        request.setTargetLanguages(Arrays.asList(targetLanguages.split("[,\\s]+")));
        try {
            releaseLanguageService.replace(principal, request);
            return "redirect:/audiences?languagesSaved";
        } catch (InvalidReleaseLanguagesException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("languagesError", messages.of(exception));
            model.addAttribute("languagesText", targetLanguages);
        }
        List<AudienceView> audiences = audienceService.list(principal.organizationId());
        return render(principal, audiences.isEmpty() ? null : audiences.getFirst().id(), model, response);
    }

    /**
     * Renders the submitted template with sample values and shows the editor again with
     * the values as entered; nothing is saved.
     */
    @PostMapping("/audiences/preview")
    String preview(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @RequestParam(name = "audienceId", required = false) UUID audienceId,
            @ModelAttribute(FORM) NewAudienceRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        try {
            model.addAttribute("preview", audienceService.preview(request.getTemplateBody()));
        } catch (InvalidAudienceTemplateException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            bindingResult.rejectValue("templateBody", exception.code(), messages.of(exception));
        }
        return render(principal, audienceId, model, response);
    }

    private String render(ReleaseFlowPrincipal principal, UUID selectedId, Model model, HttpServletResponse response) {
        UUID organizationId = principal.organizationId();
        model.addAttribute("audiences", audienceService.list(organizationId));
        model.addAttribute("variables", AudienceItem.VARIABLES);
        model.addAttribute("maxAudiences", AudienceService.MAX_AUDIENCES);
        ReleaseLanguageSettings languages = releaseLanguageService.settings(organizationId);
        model.addAttribute("releaseLanguages", languages);
        if (!model.containsAttribute("languagesText")) {
            model.addAttribute("languagesText", String.join(", ", languages.targetLanguages()));
        }
        model.addAttribute("selected", null);
        if (selectedId != null) {
            try {
                AudienceView selected = audienceService.get(organizationId, selectedId);
                model.addAttribute("selected", selected);
                if (!model.containsAttribute(FORM)) {
                    model.addAttribute(FORM, form(selected));
                }
            } catch (AudienceNotFoundException exception) {
                response.setStatus(HttpStatus.NOT_FOUND.value());
                model.addAttribute("pageError", messages.of(exception));
            }
        }
        if (!model.containsAttribute(FORM)) {
            NewAudienceRequest form = new NewAudienceRequest();
            form.setTemplateBody("- **{{whatChanged}}**{{#narrative}} — {{.}}{{/narrative}}\n");
            model.addAttribute(FORM, form);
        }
        return "audiences";
    }

    private String renderWithError(
            ReleaseFlowPrincipal principal,
            UUID audienceId,
            HttpStatus status,
            LocalizedException exception,
            Model model,
            HttpServletResponse response
    ) {
        response.setStatus(status.value());
        model.addAttribute("pageError", messages.of(exception));
        return render(principal, audienceId, model, response);
    }

    private static NewAudienceRequest form(AudienceView audience) {
        NewAudienceRequest form = new NewAudienceRequest();
        form.setCode(audience.code());
        form.setDisplayName(audience.displayName());
        form.setCommunicationIntent(audience.communicationIntent());
        form.setTemplateBody(audience.templateBody());
        return form;
    }
}
