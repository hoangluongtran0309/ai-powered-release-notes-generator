package com.hoangluongtran0309.releaseflow.category;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Reading the catalog is open to members; every write is administrator only, by URL rule.
@RestController
public class CategoryApiController {

    private final CategoryService categoryService;
    private final CategorySuggestionService suggestionService;

    CategoryApiController(CategoryService categoryService, CategorySuggestionService suggestionService) {
        this.categoryService = categoryService;
        this.suggestionService = suggestionService;
    }

    @GetMapping("/api/categories")
    List<CategoryView> list(@AuthenticationPrincipal ReleaseFlowPrincipal principal) {
        return categoryService.list(principal.organizationId());
    }

    @PostMapping("/api/categories")
    ResponseEntity<CategoryView> create(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @Valid @RequestBody NewCategoryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(principal.organizationId(), request));
    }

    @PutMapping("/api/categories/{categoryId}")
    CategoryView update(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID categoryId,
            @Valid @RequestBody CategoryRequest request
    ) {
        return categoryService.update(principal.organizationId(), categoryId, request);
    }

    // Archives the category; changes that carry it keep their snapshot.
    @DeleteMapping("/api/categories/{categoryId}")
    CategoryView archive(@AuthenticationPrincipal ReleaseFlowPrincipal principal, @PathVariable UUID categoryId) {
        return categoryService.archive(principal.organizationId(), categoryId);
    }

    @PostMapping("/api/categories/{categoryId}/unarchive")
    CategoryView unarchive(@AuthenticationPrincipal ReleaseFlowPrincipal principal, @PathVariable UUID categoryId) {
        return categoryService.unarchive(principal.organizationId(), categoryId);
    }

    @GetMapping("/api/category-suggestions")
    List<CategorySuggestionView> suggestions(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @RequestParam(required = false) CategorySuggestionStatus status
    ) {
        return suggestionService.list(principal.organizationId(), status);
    }

    @PostMapping("/api/category-suggestions/{suggestionId}/decision")
    CategorySuggestionView decide(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID suggestionId,
            @Valid @RequestBody CategorySuggestionDecisionRequest request
    ) {
        return suggestionService.decide(principal, suggestionId, request);
    }
}
