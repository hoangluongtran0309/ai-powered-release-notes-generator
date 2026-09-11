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
        this.deliveryId = deliveryId;
        this.receivedAt = receivedAt;
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
}
