package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface IntegrationSourceRepository extends JpaRepository<IntegrationSource, UUID> {

    List<IntegrationSource> findAllByOrganizationIdAndProjectIdInOrderByCreatedAtAscIdAsc(
            UUID organizationId,
            Collection<UUID> projectIds
    );

    List<IntegrationSource> findAllByOrganizationIdAndProjectIdOrderByCreatedAtAscIdAsc(UUID organizationId, UUID projectId);

    Optional<IntegrationSource> findByIdAndOrganizationIdAndProjectId(UUID id, UUID organizationId, UUID projectId);

    Optional<IntegrationSource> findByIdAndOrganizationId(UUID id, UUID organizationId);

    // The only lookup without an Organization ID: a webhook has no principal, so the
    // untrusted path routes to one source whose secret must then verify the delivery
    // before its tenant is trusted (ADR-0002).
    Optional<IntegrationSource> findByWebhookId(UUID webhookId);

    boolean existsByOrganizationIdAndSourceTypeAndExternalProjectKey(
            UUID organizationId,
            SourceType sourceType,
            String externalProjectKey
    );

    @Modifying
    @Query("""
            update IntegrationSource source
            set source.lastDeliveryAt = :deliveredAt
            where source.id = :id
              and source.organizationId = :organizationId
              and (source.lastDeliveryAt is null or source.lastDeliveryAt < :deliveredAt)
            """)
    int recordDelivery(
            @Param("id") UUID id,
            @Param("organizationId") UUID organizationId,
            @Param("deliveredAt") Instant deliveredAt
    );
}
