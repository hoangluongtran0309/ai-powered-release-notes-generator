package com.hoangluongtran0309.releaseflow.change;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ProjectSensitivePathPolicyRepository extends JpaRepository<ProjectSensitivePathPolicy, UUID> {

    Optional<ProjectSensitivePathPolicy> findByProjectIdAndOrganizationId(UUID projectId, UUID organizationId);
}
