package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.hoangluongtran0309.releaseflow.support.TestChanges;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReleaseDatabaseConstraintIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        // Published releases reject DELETE by design; TRUNCATE bypasses row triggers.
        jdbcTemplate.execute("TRUNCATE release_notes, release_changes, releases");
        jdbcTemplate.update("DELETE FROM change_processing_jobs");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void allowsOneDraftPerProjectWithATrimmedVersion() {
        UUID organization = insertOrganization();
        UUID project = insertProject(organization);
        UUID otherProject = insertProject(organization);
        insertRelease(organization, project, "1.0.0");

        assertThatThrownBy(() -> insertRelease(organization, project, "2.0.0"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertRelease(organization, otherProject, "1.0.0")).doesNotThrowAnyException();
        UUID third = insertProject(organization);
        assertThatThrownBy(() -> insertRelease(organization, third, "  "))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRelease(organization, third, " 1.0.0"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void keepsEachChangeInOneReleaseOfItsOwnProjectAndTenant() {
        UUID organization = insertOrganization();
        UUID otherOrganization = insertOrganization();
        UUID project = insertProject(organization);
        UUID otherProject = insertProject(organization);
        UUID foreignProject = insertProject(otherOrganization);
        UUID release = insertRelease(organization, project, "1.0.0");
        UUID otherRelease = insertRelease(organization, otherProject, "1.0.0");
        UUID foreignRelease = insertRelease(otherOrganization, foreignProject, "1.0.0");
        UUID change = TestChanges.insert(jdbcTemplate, organization, project, 1, "feat: a", "FEATURE", false, false, null);

        assertThatCode(() -> link(release, change, organization, project)).doesNotThrowAnyException();
        assertThatThrownBy(() -> link(otherRelease, change, organization, otherProject))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID second = TestChanges.insert(jdbcTemplate, organization, project, 2, "feat: b", "FEATURE", false, false, null);
        assertThatThrownBy(() -> link(otherRelease, second, organization, project))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> link(foreignRelease, second, otherOrganization, foreignProject))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update("DELETE FROM releases WHERE id = ?", release);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM release_changes", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM changes", Long.class)).isEqualTo(2);
    }

    @Test
    void freezesPublishedReleasesTheirChangesAndTheirNotes() {
        UUID organization = insertOrganization();
        UUID project = insertProject(organization);
        UUID publisher = insertUser(organization);
        UUID release = insertRelease(organization, project, "1.0.0");
        UUID included = TestChanges.insert(jdbcTemplate, organization, project, 1, "feat: a", "FEATURE", false, false, null);
        UUID later = TestChanges.insert(jdbcTemplate, organization, project, 2, "feat: b", "FEATURE", false, false, null);
        link(release, included, organization, project);

        assertThatCode(() -> publish(release, publisher)).doesNotThrowAnyException();
        jdbcTemplate.update(
                """
                        INSERT INTO release_notes
                            (release_id, organization_id, project_id, version, sections, markdown, published_at)
                        VALUES (?, ?, ?, '1.0.0', '[]'::jsonb, '# 1.0.0', now())
                        """,
                release, organization, project
        );

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE release_notes SET markdown = 'changed'"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("Published release notes are immutable");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM release_notes"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE releases SET version = '1.0.1' WHERE id = ?", release))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("Published releases are immutable");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM releases WHERE id = ?", release))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> link(release, later, organization, project))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("The changes of a published release are immutable");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM release_changes WHERE release_id = ?", release))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbcTemplate.queryForObject("SELECT markdown FROM release_notes", String.class)).isEqualTo("# 1.0.0");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM release_changes", Long.class)).isOne();
    }

    @Test
    void requiresARecordedPublisherAndUniqueVersions() {
        UUID organization = insertOrganization();
        UUID project = insertProject(organization);
        UUID publisher = insertUser(organization);
        UUID published = insertRelease(organization, project, "v1.0.0");
        publish(published, publisher);

        assertThatThrownBy(() -> insertRelease(organization, project, "V1.0.0"))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID draft = insertRelease(organization, project, "v1.1.0");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE releases SET status = 'PUBLISHED', published_at = now() WHERE id = ?", draft
        )).isInstanceOf(DataIntegrityViolationException.class);
        UUID outsider = insertUser(insertOrganization());
        assertThatThrownBy(() -> publish(draft, outsider)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void publish(UUID releaseId, UUID publisher) {
        jdbcTemplate.update(
                """
                        UPDATE releases
                        SET status = 'PUBLISHED', published_at = now(), published_by = ?, publisher_name = 'Publisher'
                        WHERE id = ?
                        """,
                publisher, releaseId
        );
    }

    private UUID insertUser(UUID organizationId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO app_users (id, organization_id, email, password_hash, display_name, role, created_at)
                        VALUES (?, ?, ?, 'hash', 'Publisher', 'ADMIN', now())
                        """,
                id, organizationId, id + "@example.com"
        );
        return id;
    }

    private void link(UUID releaseId, UUID changeId, UUID organizationId, UUID projectId) {
        jdbcTemplate.update(
                "INSERT INTO release_changes (release_id, change_id, organization_id, project_id, added_at) VALUES (?, ?, ?, ?, now())",
                releaseId, changeId, organizationId, projectId
        );
    }

    private UUID insertRelease(UUID organizationId, UUID projectId, String version) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO releases (id, organization_id, project_id, version, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'DRAFT', now(), now())
                        """,
                id, organizationId, projectId, version
        );
        return id;
    }

    private UUID insertOrganization() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO organizations (id, name, created_at) VALUES (?, 'Organization', now())", id);
        return id;
    }

    private UUID insertProject(UUID organizationId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO projects (id, organization_id, name, created_at) VALUES (?, ?, 'Project', now())",
                id, organizationId
        );
        return id;
    }
}
