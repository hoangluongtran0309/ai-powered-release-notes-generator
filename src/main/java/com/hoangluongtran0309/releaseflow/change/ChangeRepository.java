package com.hoangluongtran0309.releaseflow.change;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ChangeRepository extends JpaRepository<Change, UUID> {

    boolean existsByProjectIdAndOrganizationIdAndPullRequestNumber(
            UUID projectId,
            UUID organizationId,
            int pullRequestNumber
    );
}
