package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.OutputLanguage;
import com.hoangluongtran0309.releaseflow.account.OutputLanguageService;
import com.hoangluongtran0309.releaseflow.audience.AudienceBrief;
import com.hoangluongtran0309.releaseflow.audience.AudienceService;
import com.hoangluongtran0309.releaseflow.category.CategoryRef;
import com.hoangluongtran0309.releaseflow.category.CategoryService;
import com.hoangluongtran0309.releaseflow.category.CategorySuggestionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Lets a person ask the AI again for a change whose automatic attempt failed, or for
 * an Unknown change recorded before automatic classification. The AI call runs between
 * two short transactions, never inside one.
 */
@Service
class ChangeAiClassificationService {

    private final ChangeRepository changeRepository;
    private final AiClassifiers aiClassifiers;
    private final SensitivePathRules sensitivePaths;
    private final OutputLanguageService outputLanguageService;
    private final AudienceService audienceService;
    private final CategoryService categoryService;
    private final CategorySuggestionService suggestionService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    ChangeAiClassificationService(
            ChangeRepository changeRepository,
            AiClassifiers aiClassifiers,
            SensitivePathRules sensitivePaths,
            OutputLanguageService outputLanguageService,
            AudienceService audienceService,
            CategoryService categoryService,
            CategorySuggestionService suggestionService,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.changeRepository = changeRepository;
        this.aiClassifiers = aiClassifiers;
        this.sensitivePaths = sensitivePaths;
        this.outputLanguageService = outputLanguageService;
        this.audienceService = audienceService;
        this.categoryService = categoryService;
        this.suggestionService = suggestionService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    boolean isEnabled() {
        return aiClassifiers.isEnabled();
    }

    ChangeView classify(UUID organizationId, UUID projectId, UUID changeId) {
        Snapshot snapshot = transactionTemplate.execute(status -> {
            Change current = find(organizationId, projectId, changeId);
            if (current.isProcessing()) {
                throw new ChangeProcessingException();
            }
            if (!current.isAiEligible()) {
                throw new ChangeNotEligibleForAiException();
            }
            MergedPullRequest pullRequest = current.pullRequest();
            List<CategoryRef> catalog = categoryService.active(organizationId);
            return new Snapshot(
                    pullRequest,
                    ChangeClassifier.classify(pullRequest, current.recordedFiles(), sensitivePaths, catalog),
                    catalog
            );
        });
        AiChangeClassifier ai = aiClassifiers.active().orElseThrow(AiClassificationUnavailableException::new);
        OutputLanguage language = outputLanguageService.outputLanguage(organizationId);
        List<AudienceBrief> audiences = audienceService.briefs(organizationId);

        AiOutcome outcome;
        try {
            outcome = AiOutcome.succeeded(
                    ai,
                    ai.classify(AiClassificationRequest.of(changeId, snapshot.pullRequest(), language,
                            snapshot.rules().category(), snapshot.catalog(), audiences)),
                    language
            );
        } catch (AiClassificationException exception) {
            outcome = AiOutcome.failed(ai, exception.getMessage());
        }

        AiOutcome recorded = outcome;
        ChangeView view = transactionTemplate.execute(status -> {
            Change current = find(organizationId, projectId, changeId);
            // A concurrent request or review may have settled the change meanwhile; its result is kept.
            if (current.isAiEligible()) {
                ChangeAiMerge.ClassifiedChange merged = ChangeAiMerge.merge(snapshot.rules(), recorded);
                current.applyAiRetry(merged, clock.instant());
                if (merged.suggestion() != null) {
                    suggestionService.propose(organizationId, projectId, changeId, merged.suggestion());
                }
            }
            return ChangeView.from(current);
        });
        if (!outcome.succeeded()) {
            throw new AiClassificationFailedException(outcome.failure());
        }
        return view;
    }

    private Change find(UUID organizationId, UUID projectId, UUID changeId) {
        return changeRepository.findByIdAndOrganizationIdAndProjectId(changeId, organizationId, projectId)
                .orElseThrow(ChangeNotFoundException::new);
    }

    private record Snapshot(MergedPullRequest pullRequest, ChangeClassification rules, List<CategoryRef> catalog) {
    }
}
