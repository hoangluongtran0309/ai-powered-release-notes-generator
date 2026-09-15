package com.hoangluongtran0309.releaseflow.category;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The gate for categories the AI proposes. A proposal never enters the catalog on its
 * own: an administrator approves it, maps it to an existing category, or rejects it.
 * The change it came from then gets that category but still needs a person's review.
 */
@Service
public class CategorySuggestionService {

    private final CategorySuggestionRepository suggestionRepository;
    private final CategoryService categoryService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    CategorySuggestionService(
            CategorySuggestionRepository suggestionRepository,
            CategoryService categoryService,
            ApplicationEventPublisher events,
            Clock clock
    ) {
        this.suggestionRepository = suggestionRepository;
        this.categoryService = categoryService;
        this.events = events;
        this.clock = clock;
    }

    /** Records the AI's proposal for a change; called in the transaction that stores the AI result. */
    @Transactional
    public void propose(UUID organizationId, UUID projectId, UUID changeId, CategorySuggestionDraft draft) {
        if (suggestionRepository.existsByChangeId(changeId)) {
            return;
        }
        suggestionRepository.save(new CategorySuggestion(UUID.randomUUID(), organizationId, projectId, changeId, draft,
                clock.instant()));
    }

    /** Suggestions newest first; a null status lists every one. */
    @Transactional(readOnly = true)
    public List<CategorySuggestionView> list(UUID organizationId, CategorySuggestionStatus status) {
        List<CategorySuggestion> suggestions = status == null
                ? suggestionRepository.findAllByOrganizationIdOrderByCreatedAtDescIdDesc(organizationId)
                : suggestionRepository.findAllByOrganizationIdAndStatusOrderByCreatedAtDescIdDesc(organizationId, status);
        return suggestions.stream().map(CategorySuggestion::view).toList();
    }

    /** The suggestion of each change of a Project that has one, by change ID. */
    @Transactional(readOnly = true)
    public Map<UUID, CategorySuggestionView> byChange(UUID organizationId, UUID projectId) {
        return suggestionRepository.findAllByOrganizationIdAndProjectId(organizationId, projectId).stream()
                .map(CategorySuggestion::view)
                .collect(Collectors.toMap(CategorySuggestionView::changeId, Function.identity()));
    }

    @Transactional
    public CategorySuggestionView decide(
            ReleaseFlowPrincipal administrator,
            UUID suggestionId,
            CategorySuggestionDecisionRequest request
    ) {
        UUID organizationId = administrator.organizationId();
        CategorySuggestion suggestion = suggestionRepository.findByIdAndOrganizationId(suggestionId, organizationId)
                .orElseThrow(CategorySuggestionNotFoundException::new);
        suggestion.requirePending();
        CategoryRef resolved = switch (request.getDecision()) {
            case APPROVED -> categoryService.activateForSuggestion(
                    organizationId,
                    suggestion.getProposedCode(),
                    suggestion.getProposedName(),
                    suggestion.getProposedGroup()
            ).ref();
            case MAPPED -> {
                CategoryDefinition category = categoryService.get(organizationId, request.getCategoryId());
                if (!category.isActive()) {
                    throw new CategoryNotFoundException();
                }
                yield category.ref();
            }
            case REJECTED -> null;
            case PENDING_REVIEW -> throw new IllegalArgumentException("A decision must not be pending.");
        };
        suggestion.decide(
                request.getDecision(),
                resolved == null ? null : resolved.code(),
                administrator.userId(),
                administrator.displayName(),
                clock.instant()
        );
        suggestionRepository.flush();
        events.publishEvent(new CategorySuggestionDecided(
                organizationId,
                suggestion.getProjectId(),
                suggestion.getChangeId(),
                resolved
        ));
        return suggestion.view();
    }
}
