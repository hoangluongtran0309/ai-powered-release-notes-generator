package com.hoangluongtran0309.releaseflow.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface GitHubIntegrationRepository extends JpaRepository<GitHubIntegration, UUID> {

    List<GitHubIntegration> findAllByOrganizationIdAndProjectIdIn(
            UUID organizationId,
            Collection<UUID> projectIds
    );

    Optional<GitHubIntegration> findByProjectIdAndOrganizationId(UUID projectId, UUID organizationId);

    // The only lookup without an Organization ID: a webhook has no principal, so the
    // untrusted path routes to one integration whose secret must then verify the
    // delivery before its tenant is trusted (ADR-0002).
    Optional<GitHubIntegration> findByWebhookId(UUID webhookId);

    boolean existsByProjectIdAndOrganizationId(UUID projectId, UUID organizationId);

    boolean existsByOrganizationIdAndRepositoryOwnerAndRepositoryName(
            UUID organizationId,
            String repositoryOwner,
            String repositoryName
    );

    @Modifying
    @Query("""
            update GitHubIntegration integration
            set integration.lastDeliveryAt = :deliveredAt
            where integration.id = :id
              and integration.organizationId = :organizationId
              and (integration.lastDeliveryAt is null or integration.lastDeliveryAt < :deliveredAt)
            """)
    int recordDelivery(
            @Param("id") UUID id,
            @Param("organizationId") UUID organizationId,
            @Param("deliveredAt") Instant deliveredAt
    );
}
