package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.category.CategorySuggestionDecided;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Gives a change the category an administrator approved or mapped for it, inside the
 * decision's transaction. The change still needs a person's review.
 */
@Component
class CategorySuggestionDecisionListener {

    private final ChangeRepository changeRepository;

    CategorySuggestionDecisionListener(ChangeRepository changeRepository) {
        this.changeRepository = changeRepository;
    }

    @EventListener
    void onDecision(CategorySuggestionDecided decision) {
        if (decision.category() == null) {
            return;
        }
        changeRepository.findByIdAndOrganizationIdAndProjectId(
                        decision.changeId(),
                        decision.organizationId(),
                        decision.projectId()
                )
                .ifPresent(change -> change.applySuggestedCategory(decision.category()));
    }
}
