package com.hoangluongtran0309.releaseflow.audience;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface OrganizationTranslationSettingsRepository extends JpaRepository<OrganizationTranslationSettings, UUID> {
}
