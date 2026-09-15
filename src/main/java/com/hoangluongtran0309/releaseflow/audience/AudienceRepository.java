package com.hoangluongtran0309.releaseflow.audience;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AudienceRepository extends JpaRepository<AudienceDefinition, UUID> {

    List<AudienceDefinition> findAllByOrganizationIdOrderByDisplayNameAscCodeAsc(UUID organizationId);

    List<AudienceDefinition> findAllByOrganizationIdOrderByCodeAsc(UUID organizationId);

    Optional<AudienceDefinition> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);

    // Serializes changes to an Organization's set of audiences, so the count checks
    // (at least one, at most the limit) hold under concurrent requests.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AudienceDefinition a where a.organizationId = :organizationId order by a.code")
    List<AudienceDefinition> lockAllByOrganizationId(UUID organizationId);
}
