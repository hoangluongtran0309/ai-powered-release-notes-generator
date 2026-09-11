package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
class ChangeReviewService {

    private final ChangeRepository changeRepository;
    private final Clock clock;

    ChangeReviewService(ChangeRepository changeRepository, Clock clock) {
        this.changeRepository = changeRepository;
        this.clock = clock;
    }

    @Transactional
    ChangeView review(ReleaseFlowPrincipal reviewer, UUID projectId, UUID changeId, ChangeReviewRequest request) {
        Change change = changeRepository.findByIdAndOrganizationIdAndProjectId(
                        changeId,
                        reviewer.organizationId(),
                        projectId
                )
                .orElseThrow(ChangeNotFoundException::new);
        ChangeCategory category = ChangeCategory.fromValue(request.getCategory())
                .filter(value -> value != ChangeCategory.UNKNOWN)
                .orElseThrow(InvalidChangeReviewException::new);

        change.review(category, request.getBreaking(), reviewer.userId(), reviewer.displayName(), clock.instant());
        return ChangeView.from(change);
    }
}
