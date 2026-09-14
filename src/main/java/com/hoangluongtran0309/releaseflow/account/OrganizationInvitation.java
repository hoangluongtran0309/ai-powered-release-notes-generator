package com.hoangluongtran0309.releaseflow.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organization_invitations")
class OrganizationInvitation {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "accepted_by")
    private UUID acceptedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected OrganizationInvitation() {
    }

    OrganizationInvitation(
            UUID id,
            UUID organizationId,
            String email,
            String tokenHash,
            UUID createdBy,
            Instant createdAt,
            Instant expiresAt
    ) {
        this.id = id;
        this.organizationId = organizationId;
        this.email = email;
        this.tokenHash = tokenHash;
        this.status = InvitationStatus.PENDING;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.expiresAt = expiresAt;
    }

    // A pending invitation past its expiry is reported as expired before any write records it.
    InvitationStatus effectiveStatus(Instant now) {
        return status == InvitationStatus.PENDING && !now.isBefore(expiresAt) ? InvitationStatus.EXPIRED : status;
    }

    boolean isUsable(Instant now) {
        return effectiveStatus(now) == InvitationStatus.PENDING;
    }

    void accept(UUID userId, Instant at) {
        this.status = InvitationStatus.ACCEPTED;
        this.acceptedBy = userId;
        this.acceptedAt = at;
        this.updatedAt = at;
    }

    void revoke(Instant at) {
        this.status = InvitationStatus.REVOKED;
        this.revokedAt = at;
        this.updatedAt = at;
    }

    void expire(Instant at) {
        this.status = InvitationStatus.EXPIRED;
        this.updatedAt = at;
    }

    UUID getId() {
        return id;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    String getEmail() {
        return email;
    }

    InvitationStatus getStatus() {
        return status;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    UUID getCreatedBy() {
        return createdBy;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
