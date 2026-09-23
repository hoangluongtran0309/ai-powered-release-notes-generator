package com.hoangluongtran0309.releaseflow.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One category of an Organization's catalog. Its code never changes. An archived
 * category is no longer offered to the rules, the AI, or reviewers; changes that
 * already carry it keep their snapshot. A system category can be renamed only.
 */
@Entity
@Table(name = "category_definitions")
class CategoryDefinition {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 64, updatable = false)
    private String code;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_group", nullable = false, length = 20)
    private CategoryGroup group;

    @Column(name = "system_category", nullable = false, updatable = false)
    private boolean systemCategory;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CategoryDefinition() {
    }

    CategoryDefinition(
            UUID id,
            UUID organizationId,
            String code,
            String displayName,
            CategoryGroup group,
            boolean systemCategory,
            Instant createdAt
    ) {
        this.id = id;
        this.organizationId = organizationId;
        this.code = code;
        this.displayName = displayName;
        this.group = group;
        this.systemCategory = systemCategory;
        this.active = true;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    void update(String displayName, CategoryGroup group, Instant at) {
        if (systemCategory && group != this.group) {
            throw CategoryConflictException.system();
        }
        this.displayName = displayName;
        this.group = group;
        this.updatedAt = at;
    }

    void archive(Instant at) {
        if (systemCategory) {
            throw CategoryConflictException.system();
        }
        this.active = false;
        this.updatedAt = at;
    }

    void unarchive(Instant at) {
        this.active = true;
        this.updatedAt = at;
    }

    CategoryRef ref() {
        return new CategoryRef(code, displayName, group);
    }

    CategoryView view() {
        return new CategoryView(id, code, displayName, group, systemCategory, active, createdAt, updatedAt);
    }

    UUID getId() {
        return id;
    }

    String getCode() {
        return code;
    }

    boolean isActive() {
        return active;
    }
}
