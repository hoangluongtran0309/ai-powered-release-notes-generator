package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.category.CategoryGroup;
import com.hoangluongtran0309.releaseflow.category.CategoryRef;
import com.hoangluongtran0309.releaseflow.github.ChangedFile;
import com.hoangluongtran0309.releaseflow.change.ChangeAiMerge.ClassifiedChange;
import com.hoangluongtran0309.releaseflow.github.PullRequestFiles;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    // A snapshot of the catalog category, so later edits to the catalog never rewrite it.
    @Column(nullable = false, length = 64)
    private String category;

    @Column(name = "category_display_name", nullable = false, length = 120)
    private String categoryDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_group", nullable = false, length = 20)
    private CategoryGroup categoryGroup;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private ProcessingStatus processingStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "changed_file_status", length = 20)
    private ChangedFileStatus changedFileStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changed_files", columnDefinition = "jsonb")
    private List<ChangedFile> changedFiles;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "review_triggers", nullable = false, columnDefinition = "jsonb")
    private List<ReviewTrigger> reviewTriggers;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "neutral_summary", columnDefinition = "jsonb")
    private NeutralSummary neutralSummary;

    @Column(name = "content_language", length = 16)
    private String contentLanguage;

    @Column(name = "ai_provider", length = 20)
    private String aiProvider;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "audience_narratives", columnDefinition = "jsonb")
    private Map<String, String> audienceNarratives;

    @Column(name = "summary_edited_by")
    private UUID summaryEditedBy;

    @Column(name = "summary_editor_name", length = 120)
    private String summaryEditorName;

    @Column(name = "summary_edited_at")
    private Instant summaryEditedAt;

    protected Change() {
    }

    /**
     * A merged pull request that has just been received. It waits, Unknown and in
     * review, until its changed files are checked and the rules classify it.
     */
    static Change received(
            UUID id,
            UUID organizationId,
            UUID projectId,
            MergedPullRequest pullRequest,
            UUID deliveryId,
            Instant receivedAt
    ) {
        Change change = new Change();
        change.id = id;
        change.organizationId = organizationId;
        change.projectId = projectId;
        change.pullRequestNumber = pullRequest.number();
        change.title = pullRequest.title();
        change.description = pullRequest.description();
        change.authorLogin = pullRequest.authorLogin();
        change.labels = pullRequest.labels().toArray(String[]::new);
        change.targetBranch = pullRequest.targetBranch();
        change.mergeCommitSha = pullRequest.mergeCommitSha();
        change.mergedAt = pullRequest.mergedAt();
        change.url = pullRequest.url();
        change.setCategory(CategoryRef.UNKNOWN);
        change.breaking = false;
        change.needsReview = true;
        change.classificationReasons = new String[]{"Waiting for changed files"};
        change.classificationSource = ClassificationSource.RULES;
        change.aiStatus = AiStatus.NOT_REQUESTED;
        change.processingStatus = ProcessingStatus.PROCESSING;
        change.reviewTriggers = List.of();
        change.deliveryId = deliveryId;
        change.receivedAt = receivedAt;
        return change;
    }

    MergedPullRequest pullRequest() {
        return new MergedPullRequest(
                pullRequestNumber,
                title,
                description,
                authorLogin,
                List.of(labels),
                targetBranch,
                mergeCommitSha,
                mergedAt,
                url
        );
    }

    /**
     * Keeps the collected file list before the AI is asked, so a job that stops during
     * the AI call can still be classified from it without asking again.
     */
    void recordChangedFiles(PullRequestFiles files) {
        requireProcessing();
        this.changedFileStatus = files.isCollected() ? ChangedFileStatus.COLLECTED : ChangedFileStatus.UNAVAILABLE;
        this.changedFiles = files.isCollected() ? new ArrayList<>(files.files()) : null;
    }

    /** The changed files as recorded; a change recorded before collection counts as unavailable. */
    PullRequestFiles recordedFiles() {
        return changedFileStatus == ChangedFileStatus.COLLECTED
                ? PullRequestFiles.collected(changedFiles)
                : PullRequestFiles.unavailable(PullRequestFiles.UNAVAILABLE, false);
    }

    void completeProcessing(PullRequestFiles files, ClassifiedChange outcome, Instant at) {
        requireProcessing();
        recordChangedFiles(files);
        apply(outcome, List.of(), at);
        this.processingStatus = ProcessingStatus.COMPLETED;
    }

    /**
     * A person asked the AI again after a failure. Existing review triggers are kept, so
     * a change that needed review still does.
     */
    void applyAiRetry(ClassifiedChange outcome, Instant at) {
        if (!isAiEligible()) {
            throw new ChangeNotEligibleForAiException();
        }
        apply(outcome, reviewTriggers, at);
    }

    boolean isProcessing() {
        return processingStatus == ProcessingStatus.PROCESSING;
    }

    // A failed automatic attempt, or an Unknown change recorded before automatic AI.
    boolean isAiEligible() {
        return processingStatus == ProcessingStatus.COMPLETED
                && reviewedAt == null
                && (aiStatus == AiStatus.FAILED
                || (aiStatus == AiStatus.NOT_REQUESTED && getCategory().isUnknown()));
    }

    private void apply(ClassifiedChange outcome, List<ReviewTrigger> keptTriggers, Instant at) {
        ChangeClassification classification = outcome.classification();
        List<ReviewTrigger> triggers = new ArrayList<>(keptTriggers);
        classification.triggers().stream().filter(trigger -> !triggers.contains(trigger)).forEach(triggers::add);
        setCategory(classification.category());
        this.breaking = classification.breaking();
        this.needsReview = classification.needsReview() || !triggers.isEmpty();
        this.classificationReasons = classification.reasons().toArray(String[]::new);
        this.reviewTriggers = triggers;
        this.classificationSource = outcome.source();
        AiOutcome ai = outcome.ai();
        if (ai == null) {
            return;
        }
        this.aiProvider = ai.provider().getValue();
        this.aiModel = ai.model();
        this.aiAttemptedAt = at;
        if (ai.succeeded()) {
            this.aiStatus = AiStatus.SUCCEEDED;
            this.aiFailure = null;
            // A summary a person wrote is never replaced by the AI.
            if (summaryEditedAt == null) {
                this.neutralSummary = ai.classification().summary();
                this.audienceNarratives = new LinkedHashMap<>(ai.classification().narratives());
                this.contentLanguage = ai.language().tag();
            }
        } else {
            this.aiStatus = AiStatus.FAILED;
            this.aiFailure = ai.failure();
        }
    }

    private void requireProcessing() {
        if (processingStatus != ProcessingStatus.PROCESSING) {
            throw new IllegalStateException("Change " + id + " has already been processed.");
        }
    }

    // Stores exactly what the reviewer confirmed. Changing either value makes the
    // reviewer the source of the classification.
    void review(CategoryRef reviewedCategory, boolean reviewedBreaking, UUID reviewer, String name, Instant at) {
        if (isProcessing()) {
            throw new ChangeProcessingException();
        }
        if (!reviewedCategory.code().equals(category) || reviewedBreaking != breaking) {
            this.classificationSource = ClassificationSource.HUMAN;
        }
        setCategory(reviewedCategory);
        this.breaking = reviewedBreaking;
        this.needsReview = false;
        this.reviewedBy = reviewer;
        this.reviewerName = name;
        this.reviewedAt = at;
    }

    /**
     * An administrator approved or mapped the category the AI proposed for this change.
     * A change still Unknown and unreviewed takes it; it still needs a person's review.
     */
    boolean applySuggestedCategory(CategoryRef suggested) {
        if (isProcessing() || reviewedAt != null || !getCategory().isUnknown()) {
            return false;
        }
        setCategory(suggested);
        this.classificationSource = ClassificationSource.SUGGESTION;
        List<String> reasons = new ArrayList<>(List.of(classificationReasons));
        reasons.add("Category from an approved suggestion");
        this.classificationReasons = reasons.toArray(String[]::new);
        return true;
    }

    private void setCategory(CategoryRef value) {
        this.category = value.code();
        this.categoryDisplayName = value.displayName();
        this.categoryGroup = value.group();
    }

    /**
     * A person writes or corrects the summary and the narratives. From then on the AI
     * never replaces them.
     */
    void editSummary(
            NeutralSummary summary,
            Map<String, String> narratives,
            String language,
            UUID editor,
            String name,
            Instant at
    ) {
        if (isProcessing()) {
            throw new ChangeProcessingException();
        }
        this.neutralSummary = summary;
        this.audienceNarratives = new LinkedHashMap<>(narratives);
        if (contentLanguage == null) {
            this.contentLanguage = language;
        }
        this.summaryEditedBy = editor;
        this.summaryEditorName = name;
        this.summaryEditedAt = at;
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

    CategoryRef getCategory() {
        return new CategoryRef(category, categoryDisplayName, categoryGroup);
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

    ProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    ChangedFileStatus getChangedFileStatus() {
        return changedFileStatus;
    }

    List<ChangedFile> getChangedFiles() {
        return changedFiles == null ? List.of() : List.copyOf(changedFiles);
    }

    List<ReviewTrigger> getReviewTriggers() {
        return List.copyOf(reviewTriggers);
    }

    NeutralSummary getNeutralSummary() {
        return neutralSummary;
    }

    String getContentLanguage() {
        return contentLanguage;
    }

    Map<String, String> getAudienceNarratives() {
        return audienceNarratives == null ? Map.of() : Map.copyOf(audienceNarratives);
    }

    String getSummaryEditorName() {
        return summaryEditorName;
    }

    Instant getSummaryEditedAt() {
        return summaryEditedAt;
    }

    AiProvider getAiProvider() {
        return aiProvider == null ? null : AiProvider.fromValue(aiProvider).orElse(null);
    }
}
