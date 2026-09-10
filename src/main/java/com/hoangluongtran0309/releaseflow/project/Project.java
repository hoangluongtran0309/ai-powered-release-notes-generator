package com.hoangluongtran0309.releaseflow.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
class Project {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Project() {
    }

    Project(UUID id, UUID organizationId, String name, Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.name = name;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    String getName() {
        return name;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
