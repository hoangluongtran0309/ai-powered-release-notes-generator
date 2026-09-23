package com.hoangluongtran0309.releaseflow.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A category the AI proposed for one change, waiting for or holding an administrator's decision. */
@Entity
@Table(name = "category_suggestions")
class CategorySuggestion {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "change_id", nullable = false, updatable = false)
    private UUID changeId;

    @Column(name = "proposed_code", nullable = false, length = 64, updatable = false)
    private String proposedCode;

    @Column(name = "proposed_name", nullable = false, length = 120, updatable = false)
    private String proposedName;

    @Enumerated(EnumType.STRING)
    @Column(name = "proposed_group", nullable = false, length = 20, updatable = false)
    private CategoryGroup proposedGroup;

    @Column(nullable = false, length = 1000, updatable = false)
    private String rationale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategorySuggestionStatus status;

    @Column(name = "resolved_code", length = 64)
    private String resolvedCode;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decider_name", length = 120)
    private String deciderName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected CategorySuggestion() {
    }

    CategorySuggestion(UUID id, UUID organizationId, UUID projectId, UUID changeId, CategorySuggestionDraft draft, Instant at) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.changeId = changeId;
        this.proposedCode = draft.code();
        this.proposedName = draft.displayName();
        this.proposedGroup = draft.group();
        this.rationale = draft.rationale();
        this.status = CategorySuggestionStatus.PENDING_REVIEW;
        this.createdAt = at;
    }

    void requirePending() {
        if (status != CategorySuggestionStatus.PENDING_REVIEW) {
            throw CategoryConflictException.decided();
        }
    }

    void decide(CategorySuggestionStatus decision, String resolvedCode, UUID decider, String name, Instant at) {
        requirePending();
        this.status = decision;
        this.resolvedCode = resolvedCode;
        this.decidedBy = decider;
        this.deciderName = name;
        this.decidedAt = at;
    }

    CategorySuggestionView view() {
        return new CategorySuggestionView(id, projectId, changeId, proposedCode, proposedName, proposedGroup, rationale,
                status, resolvedCode, deciderName, createdAt, decidedAt);
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getProjectId() {
        return projectId;
    }

    UUID getChangeId() {
        return changeId;
    }

    String getProposedCode() {
        return proposedCode;
    }

    String getProposedName() {
        return proposedName;
    }

    CategoryGroup getProposedGroup() {
        return proposedGroup;
    }
}
