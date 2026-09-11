package com.hoangluongtran0309.releaseflow.change;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Asks OpenAI for a suggestion on one Unknown change. The network call runs between
 * two short transactions, never inside one.
 */
@Service
class ChangeAiClassificationService {

    private final ChangeRepository changeRepository;
    private final OpenAiChangeClassifier classifier;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    ChangeAiClassificationService(
            ChangeRepository changeRepository,
            OpenAiChangeClassifier classifier,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.changeRepository = changeRepository;
        this.classifier = classifier;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    boolean isEnabled() {
        return classifier.isEnabled();
    }

    ChangeView classify(UUID organizationId, UUID projectId, UUID changeId) {
        ChangeView change = transactionTemplate.execute(status -> {
            Change current = find(organizationId, projectId, changeId);
            if (!current.isAiEligible()) {
                throw new ChangeNotEligibleForAiException();
            }
            return ChangeView.from(current);
        });
        if (!classifier.isEnabled()) {
            throw new AiClassificationUnavailableException();
        }

        AiClassification suggestion;
        try {
            suggestion = classifier.classify(change);
        } catch (OpenAiClassificationException exception) {
            record(organizationId, projectId, changeId, current -> current.recordAiFailure(
                    exception.getMessage(),
                    clock.instant()
            ));
            throw new AiClassificationFailedException(exception.getMessage());
        }
        return record(organizationId, projectId, changeId, current -> current.applyAiSuggestion(
                suggestion,
                classifier.model(),
                clock.instant()
        ));
    }

    // A concurrent request may have classified the change meanwhile; its result is kept.
    private ChangeView record(
            UUID organizationId,
            UUID projectId,
            UUID changeId,
            Consumer<Change> update
    ) {
        return transactionTemplate.execute(status -> {
            Change current = find(organizationId, projectId, changeId);
            if (current.isAiEligible()) {
                update.accept(current);
            }
            return ChangeView.from(current);
        });
    }

    private Change find(UUID organizationId, UUID projectId, UUID changeId) {
        return changeRepository.findByIdAndOrganizationIdAndProjectId(changeId, organizationId, projectId)
                .orElseThrow(ChangeNotFoundException::new);
    }
}
