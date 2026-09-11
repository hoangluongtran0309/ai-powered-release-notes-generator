package com.hoangluongtran0309.releaseflow.release;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "release_changes")
@IdClass(ReleaseChange.Key.class)
class ReleaseChange {

    @Id
    @Column(name = "release_id")
    private UUID releaseId;

    @Id
    @Column(name = "change_id")
    private UUID changeId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    protected ReleaseChange() {
    }

    ReleaseChange(UUID releaseId, UUID changeId, UUID organizationId, UUID projectId, Instant addedAt) {
        this.releaseId = releaseId;
        this.changeId = changeId;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.addedAt = addedAt;
    }

    UUID getReleaseId() {
        return releaseId;
    }

    UUID getChangeId() {
        return changeId;
    }

    record Key(UUID releaseId, UUID changeId) implements Serializable {

        // JPA instantiates id classes reflectively.
        Key() {
            this(null, null);
        }
    }
}
