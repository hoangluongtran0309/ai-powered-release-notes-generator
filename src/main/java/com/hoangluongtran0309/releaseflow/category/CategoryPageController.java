package com.hoangluongtran0309.releaseflow.category;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
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
public class CategoryPageController {

    private static final String FORM = "categoryForm";

    private final CategoryService categoryService;
    private final CategorySuggestionService suggestionService;

    CategoryPageController(CategoryService categoryService, CategorySuggestionService suggestionService) {
        this.categoryService = categoryService;
        this.suggestionService = suggestionService;
    }

    @GetMapping("/categories")
    String categories(@AuthenticationPrincipal ReleaseFlowPrincipal principal, Model model) {
        return render(principal, model);
    }

    @PostMapping("/categories")
    String create(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @Valid @ModelAttribute(FORM) NewCategoryRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return render(principal, model);
        }
        try {
            categoryService.create(principal.organizationId(), request);
        } catch (CategoryConflictException exception) {
            response.setStatus(HttpStatus.CONFLICT.value());
            bindingResult.rejectValue("code", exception.code(), exception.getMessage());
            return render(principal, model);
        }
        return "redirect:/categories?saved";
    }

    @PostMapping("/categories/{categoryId}")
    String update(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID categoryId,
            @Valid @ModelAttribute("updateForm") CategoryRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("pageError", bindingResult.getAllErrors().getFirst().getDefaultMessage());
            return render(principal, model);
        }
        return act(principal, model, response, "/categories?saved",
                () -> categoryService.update(principal.organizationId(), categoryId, request));
    }

    @PostMapping("/categories/{categoryId}/archive")
    String archive(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID categoryId,
            Model model,
            HttpServletResponse response
    ) {
        return act(principal, model, response, "/categories?archived",
                () -> categoryService.archive(principal.organizationId(), categoryId));
    }

    @PostMapping("/categories/{categoryId}/unarchive")
    String unarchive(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID categoryId,
            Model model,
            HttpServletResponse response
    ) {
        return act(principal, model, response, "/categories?restored",
                () -> categoryService.unarchive(principal.organizationId(), categoryId));
    }

    /**
     * Decides a suggestion from this page or from the Change Inbox, then returns there.
     * Only paths inside this application are followed.
     */
    @PostMapping("/categories/suggestions/{suggestionId}/decision")
    String decide(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID suggestionId,
            @Valid @ModelAttribute CategorySuggestionDecisionRequest request,
            BindingResult bindingResult,
            @RequestParam(name = "returnTo", required = false) String returnTo,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("pageError", "Approve the proposal, map it to an existing category, or reject it.");
            return render(principal, model);
        }
        String target = returnTo != null && (returnTo.startsWith("/changes?") || returnTo.equals("/changes"))
                ? returnTo
                : "/categories?decided";
        return act(principal, model, response, target,
                () -> suggestionService.decide(principal, suggestionId, request));
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
        } catch (CategoryNotFoundException | CategorySuggestionNotFoundException exception) {
            return renderWithError(principal, HttpStatus.NOT_FOUND, exception, model, response);
        } catch (CategoryConflictException exception) {
            return renderWithError(principal, HttpStatus.CONFLICT, exception, model, response);
        }
        return "redirect:" + successPath;
    }

    private String render(ReleaseFlowPrincipal principal, Model model) {
        UUID organizationId = principal.organizationId();
        model.addAttribute("categories", categoryService.list(organizationId));
        model.addAttribute("activeCategories", categoryService.active(organizationId).stream()
                .filter(category -> !category.isUnknown())
                .toList());
        model.addAttribute("groups", CategoryGroup.values());
        model.addAttribute("suggestions", suggestionService.list(organizationId, null));
        if (!model.containsAttribute(FORM)) {
            model.addAttribute(FORM, new NewCategoryRequest());
        }
        return "categories";
    }

    private String renderWithError(
            ReleaseFlowPrincipal principal,
            HttpStatus status,
            RuntimeException exception,
            Model model,
            HttpServletResponse response
    ) {
        response.setStatus(status.value());
        model.addAttribute("pageError", exception.getMessage());
        return render(principal, model);
    }
}
