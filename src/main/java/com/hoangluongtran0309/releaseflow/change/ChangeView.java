package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.category.CategoryGroup;
import com.hoangluongtran0309.releaseflow.category.CategoryRef;
import com.hoangluongtran0309.releaseflow.github.ChangedFile;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
        String category,
        String categoryName,
        CategoryGroup categoryGroup,
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
        Instant reviewedAt,
        ProcessingStatus processingStatus,
        ChangedFileStatus changedFileStatus,
        List<ChangedFile> changedFiles,
        List<ReviewTrigger> reviewTriggers,
        NeutralSummary neutralSummary,
        String contentLanguage,
        AiProvider aiProvider,
        Map<String, String> audienceNarratives,
        String summaryEditorName,
        Instant summaryEditedAt,
        ContextAssessment context
) {

    public ChangeView {
        // Sorted by audience code so pages list narratives in a stable order.
        audienceNarratives = audienceNarratives == null
                ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(audienceNarratives));
    }

    /** What this change says to one audience, or an empty string. */
    public String narrative(String audienceCode) {
        return audienceNarratives.getOrDefault(audienceCode, "");
    }

    /** The category the change carries, as a snapshot. */
    public CategoryRef categoryRef() {
        return new CategoryRef(category, categoryName, categoryGroup);
    }

    public boolean unknownCategory() {
        return CategoryRef.UNKNOWN.code().equals(category);
    }

    public boolean processing() {
        return processingStatus == ProcessingStatus.PROCESSING;
    }

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
                change.getCategory().code(),
                change.getCategory().displayName(),
                change.getCategory().group(),
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
                change.getReviewedAt(),
                change.getProcessingStatus(),
                change.getChangedFileStatus(),
                change.getChangedFiles(),
                change.getReviewTriggers(),
                change.getNeutralSummary(),
                change.getContentLanguage(),
                change.getAiProvider(),
                change.getAudienceNarratives(),
                change.getSummaryEditorName(),
                change.getSummaryEditedAt(),
                change.getContext()
        );
    }
}
