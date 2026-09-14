package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangeDatabaseConstraintIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String VALID_SHA = "0123456789abcdef0123456789abcdef01234567";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM change_processing_jobs");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
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

    @Test
    void requiresReviewForBreakingAndUnknownChanges() {
        UUID organization = insertOrganization("First");
        UUID project = insertProject(organization);

        assertThatThrownBy(() -> insertChange(organization, project, 1, "Title", VALID_SHA, "OTHER", false, true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChange(organization, project, 2, "Title", VALID_SHA, "FEATURE", true, false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChange(organization, project, 3, "Title", VALID_SHA, "UNKNOWN", false, false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertChange(organization, project, 4, "Title", VALID_SHA, "FEATURE", true, true))
                .doesNotThrowAnyException();
        assertThatCode(() -> insertChange(organization, project, 5, "Title", VALID_SHA, "UNKNOWN", false, true))
                .doesNotThrowAnyException();
    }

    // Since ADR-0009 an AI result may settle a change, and may accompany a category the rules chose.
    @Test
    void keepsAiStateConsistent() {
        UUID organization = insertOrganization("First");
        UUID project = insertProject(organization);
        Instant now = Instant.now();

        assertThatCode(() -> insertChange(organization, project, 1, "Title", VALID_SHA, "FIX", false, true,
                "AI", "SUCCEEDED", "test-model", null, now)).doesNotThrowAnyException();
        assertThatCode(() -> insertChange(organization, project, 2, "Title", VALID_SHA, "UNKNOWN", false, true,
                "RULES", "FAILED", null, "OpenAI returned HTTP 500.", now)).doesNotThrowAnyException();

        assertThatCode(() -> insertChange(organization, project, 3, "Title", VALID_SHA, "FIX", false, false,
                "AI", "SUCCEEDED", "test-model", null, now)).doesNotThrowAnyException();
        assertThatThrownBy(() -> insertChange(organization, project, 4, "Title", VALID_SHA, "FIX", false, true,
                "AI", "SUCCEEDED", null, null, now)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertChange(organization, project, 5, "Title", VALID_SHA, "FIX", false, true,
                "RULES", "SUCCEEDED", "test-model", null, now)).doesNotThrowAnyException();
        assertThatThrownBy(() -> insertChange(organization, project, 6, "Title", VALID_SHA, "UNKNOWN", false, true,
                "RULES", "FAILED", null, null, now)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChange(organization, project, 7, "Title", VALID_SHA, "UNKNOWN", false, true,
                "RULES", "NOT_REQUESTED", null, null, now)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChange(organization, project, 8, "Title", VALID_SHA, "UNKNOWN", false, true,
                "HUMAN", "NOT_REQUESTED", null, null, null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void recordsReviewsOnlyForSettledChangesByReviewersOfTheSameTenant() {
        UUID organization = insertOrganization("First");
        UUID otherOrganization = insertOrganization("Second");
        UUID project = insertProject(organization);
        UUID reviewer = insertUser(organization, "reviewer@example.com");
        UUID outsider = insertUser(otherOrganization, "outsider@example.com");

        assertThatCode(() -> insertReviewed(organization, project, 1, "FIX", true, false, "RULES", "NOT_REQUESTED", null, reviewer))
                .doesNotThrowAnyException();
        assertThatCode(() -> insertReviewed(organization, project, 2, "FIX", false, false, "AI", "SUCCEEDED", "test-model", reviewer))
                .doesNotThrowAnyException();
        assertThatCode(() -> insertReviewed(organization, project, 3, "FIX", false, false, "HUMAN", "SUCCEEDED", "test-model", reviewer))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> insertReviewed(organization, project, 4, "FIX", false, false, "RULES", "NOT_REQUESTED", null, outsider))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertReviewed(organization, project, 5, "UNKNOWN", false, false, "HUMAN", "NOT_REQUESTED", null, reviewer))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertReviewed(organization, project, 6, "FIX", false, true, "HUMAN", "NOT_REQUESTED", null, reviewer))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChange(organization, project, 7, "Title", VALID_SHA, "FIX", false, false,
                "HUMAN", "NOT_REQUESTED", null, null, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChange(organization, project, 8, "Title", VALID_SHA, "FIX", true, false,
                "RULES", "NOT_REQUESTED", null, null, null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void marksChangesRecordedBeforeV4AsUnknownAndNeedingReview() {
        String schema = "v4_backfill_check";
        try {
            migrate(schema, "3");
            UUID organization = UUID.randomUUID();
            UUID project = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO " + schema + ".organizations (id, name, created_at) VALUES (?, 'Old', now())",
                    organization);
            jdbcTemplate.update("INSERT INTO " + schema + ".projects (id, organization_id, name, created_at) VALUES (?, ?, 'Old', now())",
                    project, organization);
            jdbcTemplate.update(
                    "INSERT INTO " + schema + """
                            .changes (id, organization_id, project_id, pull_request_number, title, author_login,
                                labels, target_branch, merge_commit_sha, merged_at, url, delivery_id, received_at)
                            VALUES (?, ?, ?, 1, 'feat: recorded before V4', 'octocat', '{}', 'main', ?, now(),
                                'https://github.com/acme/releaseflow/pull/1', ?, now())
                            """,
                    UUID.randomUUID(), organization, project, VALID_SHA, UUID.randomUUID()
            );

            migrate(schema, "4");

            assertThat(jdbcTemplate.queryForMap(
                    "SELECT category, breaking, needs_review, array_to_string(classification_reasons, '|') AS reasons FROM "
                            + schema + ".changes"
            )).containsEntry("category", "UNKNOWN")
                    .containsEntry("breaking", false)
                    .containsEntry("needs_review", true)
                    .containsEntry("reasons", "Recorded before rule-based classification");
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private void migrate(String schema, String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private UUID insertUser(UUID organizationId, String email) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO app_users (id, organization_id, email, password_hash, display_name, role, created_at)
                        VALUES (?, ?, ?, 'hash', 'Reviewer', 'ADMIN', now())
                        """,
                id,
                organizationId,
                email
        );
        return id;
    }

    private void insertReviewed(
            UUID organizationId,
            UUID projectId,
            int number,
            String category,
            boolean breaking,
            boolean needsReview,
            String source,
            String aiStatus,
            String aiModel,
            UUID reviewer
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO changes
                            (id, organization_id, project_id, pull_request_number, title, author_login, labels,
                             target_branch, merge_commit_sha, merged_at, url, delivery_id, received_at,
                             category, breaking, needs_review, classification_reasons,
                             classification_source, ai_status, ai_provider, ai_model, ai_attempted_at,
                             reviewed_by, reviewer_name, reviewed_at, processing_status, review_triggers)
                        VALUES (?, ?, ?, ?, 'Title', 'octocat', '{}', 'main', ?, now(),
                                'https://github.com/acme/releaseflow/pull/1', ?, now(),
                                ?, ?, ?, '{"Reviewed"}', ?, ?, ?, ?, ?, ?, 'Reviewer', now(), 'COMPLETED', '[]')
                        """,
                UUID.randomUUID(),
                organizationId,
                projectId,
                number,
                VALID_SHA,
                UUID.randomUUID(),
                category,
                breaking,
                needsReview,
                source,
                aiStatus,
                "NOT_REQUESTED".equals(aiStatus) ? null : "openai",
                aiModel,
                "NOT_REQUESTED".equals(aiStatus) ? null : Timestamp.from(Instant.now()),
                reviewer
        );
    }

    private UUID insertOrganization(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO organizations (id, name, created_at, output_language) VALUES (?, ?, ?, 'en')",
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
        insertChange(organizationId, projectId, number, title, sha, "FEATURE", false, false);
    }

    private void insertChange(
            UUID organizationId,
            UUID projectId,
            int number,
            String title,
            String sha,
            String category,
            boolean breaking,
            boolean needsReview
    ) {
        insertChange(organizationId, projectId, number, title, sha, category, breaking, needsReview,
                "RULES", "NOT_REQUESTED", null, null, null);
    }

    private void insertChange(
            UUID organizationId,
            UUID projectId,
            int number,
            String title,
            String sha,
            String category,
            boolean breaking,
            boolean needsReview,
            String source,
            String aiStatus,
            String aiModel,
            String aiFailure,
            Instant aiAttemptedAt
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO changes
                            (id, organization_id, project_id, pull_request_number, title, author_login, labels,
                             target_branch, merge_commit_sha, merged_at, url, delivery_id, received_at,
                             category, breaking, needs_review, classification_reasons,
                             classification_source, ai_status, ai_provider, ai_model, ai_failure, ai_attempted_at,
                             processing_status, review_triggers)
                        VALUES (?, ?, ?, ?, ?, 'octocat', '{}', 'main', ?, ?, ?, ?, ?, ?, ?, ?, '{"Title type \\"feat\\""}',
                                ?, ?, ?, ?, ?, ?, 'COMPLETED', '[]')
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
                Timestamp.from(Instant.now()),
                category,
                breaking,
                needsReview,
                source,
                aiStatus,
                "NOT_REQUESTED".equals(aiStatus) ? null : "openai",
                aiModel,
                aiFailure,
                aiAttemptedAt == null ? null : Timestamp.from(aiAttemptedAt)
        );
    }
}
