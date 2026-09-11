package com.hoangluongtran0309.releaseflow.change;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "changes")
class Change {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "pull_request_number", nullable = false)
    private int pullRequestNumber;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "author_login", nullable = false, length = 100)
    private String authorLogin;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] labels;

    @Column(name = "target_branch", nullable = false, length = 255)
    private String targetBranch;

    @Column(name = "merge_commit_sha", nullable = false, length = 64)
    private String mergeCommitSha;

    @Column(name = "merged_at", nullable = false)
    private Instant mergedAt;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChangeCategory category;

    @Column(nullable = false)
    private boolean breaking;

    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "classification_reasons", nullable = false, columnDefinition = "text[]")
    private String[] classificationReasons;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification_source", nullable = false, length = 10)
    private ClassificationSource classificationSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_status", nullable = false, length = 20)
    private AiStatus aiStatus;

    @Column(name = "ai_model", length = 100)
    private String aiModel;

    @Column(name = "ai_failure", length = 300)
    private String aiFailure;

    @Column(name = "ai_attempted_at")
    private Instant aiAttemptedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewer_name", length = 120)
    private String reviewerName;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected Change() {
    }

    Change(
            UUID id,
            UUID organizationId,
            UUID projectId,
            MergedPullRequest pullRequest,
            ChangeClassification classification,
            UUID deliveryId,
            Instant receivedAt
    ) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.pullRequestNumber = pullRequest.number();
        this.title = pullRequest.title();
        this.description = pullRequest.description();
        this.authorLogin = pullRequest.authorLogin();
        this.labels = pullRequest.labels().toArray(String[]::new);
        this.targetBranch = pullRequest.targetBranch();
        this.mergeCommitSha = pullRequest.mergeCommitSha();
        this.mergedAt = pullRequest.mergedAt();
        this.url = pullRequest.url();
        this.category = classification.category();
        this.breaking = classification.breaking();
        this.needsReview = classification.needsReview();
        this.classificationReasons = classification.reasons().toArray(String[]::new);
        this.classificationSource = ClassificationSource.RULES;
        this.aiStatus = AiStatus.NOT_REQUESTED;
        this.deliveryId = deliveryId;
        this.receivedAt = receivedAt;
    }

    boolean isAiEligible() {
        return category == ChangeCategory.UNKNOWN && classificationSource == ClassificationSource.RULES;
    }

    // AI can only add caution: it never clears a breaking flag or the need for review.
    void applyAiSuggestion(AiClassification suggestion, String model, Instant attemptedAt) {
        this.category = suggestion.category();
        this.breaking = breaking || suggestion.breaking();
        this.needsReview = true;
        this.classificationSource = ClassificationSource.AI;
        this.aiStatus = AiStatus.SUCCEEDED;
        this.aiModel = model;
        this.aiFailure = null;
        this.aiAttemptedAt = attemptedAt;
        String[] reasons = Arrays.copyOf(classificationReasons, classificationReasons.length + 1);
        reasons[classificationReasons.length] = "AI suggestion (" + model + "): " + suggestion.rationale();
        this.classificationReasons = reasons;
    }

    // Stores exactly what the reviewer confirmed. Changing either value makes the
    // reviewer the source of the classification.
    void review(ChangeCategory reviewedCategory, boolean reviewedBreaking, UUID reviewer, String name, Instant at) {
        if (reviewedCategory != category || reviewedBreaking != breaking) {
            this.classificationSource = ClassificationSource.HUMAN;
        }
        this.category = reviewedCategory;
        this.breaking = reviewedBreaking;
        this.needsReview = false;
        this.reviewedBy = reviewer;
        this.reviewerName = name;
        this.reviewedAt = at;
    }

    void recordAiFailure(String failure, Instant attemptedAt) {
        this.aiStatus = AiStatus.FAILED;
        this.aiFailure = failure;
        this.aiAttemptedAt = attemptedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getProjectId() {
        return projectId;
    }

    int getPullRequestNumber() {
        return pullRequestNumber;
    }

    String getTitle() {
        return title;
    }

    String getDescription() {
        return description;
    }

    String getAuthorLogin() {
        return authorLogin;
    }

    List<String> getLabels() {
        return List.of(labels);
    }

    String getTargetBranch() {
        return targetBranch;
    }

    String getMergeCommitSha() {
        return mergeCommitSha;
    }

    Instant getMergedAt() {
        return mergedAt;
    }

    String getUrl() {
        return url;
    }

    UUID getDeliveryId() {
        return deliveryId;
    }

    Instant getReceivedAt() {
        return receivedAt;
    }

    ChangeCategory getCategory() {
        return category;
    }

    boolean isBreaking() {
        return breaking;
    }

    boolean isNeedsReview() {
        return needsReview;
    }

    List<String> getClassificationReasons() {
        return List.of(classificationReasons);
    }

    ClassificationSource getClassificationSource() {
        return classificationSource;
    }

    AiStatus getAiStatus() {
        return aiStatus;
    }

    String getAiModel() {
        return aiModel;
    }

    String getAiFailure() {
        return aiFailure;
    }

    Instant getAiAttemptedAt() {
        return aiAttemptedAt;
    }

    UUID getReviewedBy() {
        return reviewedBy;
    }

    String getReviewerName() {
        return reviewerName;
    }

    Instant getReviewedAt() {
        return reviewedAt;
    }
}
