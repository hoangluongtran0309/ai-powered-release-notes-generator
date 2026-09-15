package com.hoangluongtran0309.releaseflow.audience;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AudienceTemplateVariantRepository extends JpaRepository<AudienceTemplateVariant, UUID> {

    List<AudienceTemplateVariant> findAllByOrganizationIdOrderByLanguageAsc(UUID organizationId);

    Optional<AudienceTemplateVariant> findByAudienceIdAndOrganizationIdAndLanguage(
            UUID audienceId,
            UUID organizationId,
            String language
    );
}
