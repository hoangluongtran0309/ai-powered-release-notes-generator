package com.hoangluongtran0309.releaseflow.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findAllByOrganizationIdOrderByCreatedAtAscIdAsc(UUID organizationId);

    Optional<Project> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
