package com.hoangluongtran0309.releaseflow.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CategoryRepository extends JpaRepository<CategoryDefinition, UUID> {

    List<CategoryDefinition> findAllByOrganizationIdOrderByCodeAsc(UUID organizationId);

    List<CategoryDefinition> findAllByOrganizationIdAndActiveTrueOrderByCodeAsc(UUID organizationId);

    Optional<CategoryDefinition> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<CategoryDefinition> findByOrganizationIdAndCode(UUID organizationId, String code);
}
