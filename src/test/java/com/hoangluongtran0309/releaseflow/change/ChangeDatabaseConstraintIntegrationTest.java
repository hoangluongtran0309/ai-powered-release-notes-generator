package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangeDatabaseConstraintIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String VALID_SHA = "0123456789abcdef0123456789abcdef01234567";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void recordsOneChangePerPullRequestWithinAProject() {
        UUID organization = insertOrganization("First");
        UUID project = insertProject(organization);
        UUID anotherProject = insertProject(organization);
        insertChange(organization, project, 1, "Title", VALID_SHA);

        assertThatThrownBy(() -> insertChange(organization, project, 1, "Other", VALID_SHA))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertChange(organization, anotherProject, 1, "Title", VALID_SHA))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsCrossTenantProjectLinksAndInvalidValues() {
        UUID firstOrganization = insertOrganization("First");
        UUID secondOrganization = insertOrganization("Second");
        UUID firstProject = insertProject(firstOrganization);

        assertThatThrownBy(() -> insertChange(secondOrganization, firstProject, 1, "Title", VALID_SHA))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChange(firstOrganization, firstProject, 0, "Title", VALID_SHA))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChange(firstOrganization, firstProject, 1, "   ", VALID_SHA))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChange(firstOrganization, firstProject, 1, "Title", VALID_SHA.toUpperCase()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChange(firstOrganization, firstProject, 1, "Title", "abc123"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertOrganization(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO organizations (id, name, created_at) VALUES (?, ?, ?)",
                id,
                name,
                Timestamp.from(Instant.now())
        );
        return id;
    }

    private UUID insertProject(UUID organizationId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO projects (id, organization_id, name, created_at) VALUES (?, ?, ?, ?)",
                id,
                organizationId,
                "Project",
                Timestamp.from(Instant.now())
        );
        return id;
    }

    private void insertChange(UUID organizationId, UUID projectId, int number, String title, String sha) {
        jdbcTemplate.update(
                """
                        INSERT INTO changes
                            (id, organization_id, project_id, pull_request_number, title, author_login, labels,
                             target_branch, merge_commit_sha, merged_at, url, delivery_id, received_at)
                        VALUES (?, ?, ?, ?, ?, 'octocat', '{}', 'main', ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                organizationId,
                projectId,
                number,
                title,
                sha,
                Timestamp.from(Instant.now()),
                "https://github.com/acme/releaseflow/pull/" + number,
                UUID.randomUUID(),
                Timestamp.from(Instant.now())
        );
    }
}
