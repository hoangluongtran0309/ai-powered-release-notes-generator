package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.OutputLanguage;

import java.util.List;
import java.util.UUID;

/**
 * What is sent to the AI for one change. The author is deliberately not included.
 *
 * @param lockedCategory the category the fixed rules chose, which the AI must return,
 *     or null when the rules left the change Unknown
 */
record AiClassificationRequest(
        UUID changeId,
        String title,
        String description,
        List<String> labels,
        String targetBranch,
        OutputLanguage outputLanguage,
        ChangeCategory lockedCategory
) {

    AiClassificationRequest {
        labels = List.copyOf(labels);
    }

    static AiClassificationRequest of(
            UUID changeId,
            MergedPullRequest pullRequest,
            OutputLanguage outputLanguage,
            ChangeCategory rulesCategory
    ) {
        return new AiClassificationRequest(
                changeId,
                pullRequest.title(),
                pullRequest.description(),
                pullRequest.labels(),
                pullRequest.targetBranch(),
                outputLanguage,
                rulesCategory == ChangeCategory.UNKNOWN ? null : rulesCategory
        );
    }
}
