package com.hoangluongtran0309.releaseflow.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CategorySuggestionRepository extends JpaRepository<CategorySuggestion, UUID> {

    List<CategorySuggestion> findAllByOrganizationIdOrderByCreatedAtDescIdDesc(UUID organizationId);

    List<CategorySuggestion> findAllByOrganizationIdAndStatusOrderByCreatedAtDescIdDesc(
            UUID organizationId,
            CategorySuggestionStatus status
    );

    List<CategorySuggestion> findAllByOrganizationIdAndProjectId(UUID organizationId, UUID projectId);

    Optional<CategorySuggestion> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByChangeId(UUID changeId);
}
