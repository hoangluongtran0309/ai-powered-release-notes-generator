package com.hoangluongtran0309.releaseflow.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface OrganizationInvitationRepository extends JpaRepository<OrganizationInvitation, UUID> {

    Optional<OrganizationInvitation> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<OrganizationInvitation> findAllByOrganizationIdOrderByCreatedAtDescIdDesc(UUID organizationId);

    Optional<OrganizationInvitation> findByOrganizationIdAndEmailAndStatus(
            UUID organizationId,
            String email,
            InvitationStatus status
    );

    Optional<OrganizationInvitation> findFirstByTokenHash(String tokenHash);

    // Locks the row so two concurrent acceptances of one token cannot both succeed.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OrganizationInvitation> findByTokenHash(String tokenHash);
}
