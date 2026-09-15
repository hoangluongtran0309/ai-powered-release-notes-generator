package com.hoangluongtran0309.releaseflow.change;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface DuplicateCandidateRepository extends JpaRepository<DuplicateCandidate, UUID> {

    List<DuplicateCandidate> findAllByOrganizationIdAndProjectIdOrderByCreatedAtDescIdDesc(UUID organizationId, UUID projectId);

    Optional<DuplicateCandidate> findByIdAndOrganizationIdAndProjectId(UUID id, UUID organizationId, UUID projectId);

    boolean existsByChangeIdAndDuplicateOfId(UUID changeId, UUID duplicateOfId);
}
