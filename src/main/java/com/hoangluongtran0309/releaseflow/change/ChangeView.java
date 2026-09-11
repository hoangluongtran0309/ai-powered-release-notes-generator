package com.hoangluongtran0309.releaseflow.change;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChangeView(
        UUID id,
        int pullRequestNumber,
        String title,
        String description,
        String authorLogin,
        List<String> labels,
        String targetBranch,
        String mergeCommitSha,
        Instant mergedAt,
        String url,
        ChangeCategory category,
        boolean breaking,
        boolean needsReview,
        List<String> reasons,
        ClassificationSource classificationSource,
        AiStatus aiStatus,
        String aiModel,
        String aiFailure,
        boolean aiEligible,
        UUID reviewedBy,
        String reviewerName,
        Instant reviewedAt
) {

    static ChangeView from(Change change) {
        return new ChangeView(
                change.getId(),
                change.getPullRequestNumber(),
                change.getTitle(),
                change.getDescription(),
                change.getAuthorLogin(),
                change.getLabels(),
                change.getTargetBranch(),
                change.getMergeCommitSha(),
                change.getMergedAt(),
                change.getUrl(),
                change.getCategory(),
                change.isBreaking(),
                change.isNeedsReview(),
                change.getClassificationReasons(),
                change.getClassificationSource(),
                change.getAiStatus(),
                change.getAiModel(),
                change.getAiFailure(),
                change.isAiEligible(),
                change.getReviewedBy(),
                change.getReviewerName(),
                change.getReviewedAt()
        );
    }
}
