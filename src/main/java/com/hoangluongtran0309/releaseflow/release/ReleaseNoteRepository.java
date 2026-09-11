package com.hoangluongtran0309.releaseflow.release;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ReleaseNoteRepository extends JpaRepository<ReleaseNote, UUID> {

    Optional<ReleaseNote> findByReleaseIdAndOrganizationIdAndProjectId(UUID releaseId, UUID organizationId, UUID projectId);
}
