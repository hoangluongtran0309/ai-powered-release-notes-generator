package com.hoangluongtran0309.releaseflow.project;

import org.springframework.data.jpa.repository.JpaRepository;

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

    boolean existsByProjectIdAndOrganizationId(UUID projectId, UUID organizationId);

    boolean existsByOrganizationIdAndRepositoryOwnerAndRepositoryName(
            UUID organizationId,
            String repositoryOwner,
            String repositoryName
    );
}
