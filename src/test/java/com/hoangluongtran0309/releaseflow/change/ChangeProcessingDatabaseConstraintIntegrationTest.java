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

class ChangeProcessingDatabaseConstraintIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String SENSITIVE_TRIGGER = "[{\"type\":\"SENSITIVE_PATH\",\"detail\":\"db/migration/V2.sql\"}]";
    private static final String ONE_FILE = "[{\"path\":\"src/App.java\",\"previousPath\":null,\"kind\":\"MODIFIED\"}]";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID organization;
    private UUID project;

    @BeforeEach
    void createProject() {
        clearDatabase();
        organization = UUID.randomUUID();
        project = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO organizations (id, name, created_at) VALUES (?, 'Acme', now())", organization);
        jdbcTemplate.update(
                "INSERT INTO projects (id, organization_id, name, created_at) VALUES (?, ?, 'Project', now())",
                project,
                organization
        );
    }

    @AfterEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM change_processing_jobs");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM github_integrations");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void aProcessingChangeStaysUnsettledAndUnclassified() {
        assertThatCode(() -> insertChange(1, "PROCESSING", "UNKNOWN", true, null, null, "[]"))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> insertChange(2, "PROCESSING", "FEATURE", false, null, null, "[]"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChange(3, "PROCESSING", "UNKNOWN", true, "COLLECTED", ONE_FILE, "[]"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChange(4, "PROCESSING", "UNKNOWN", true, null, null, SENSITIVE_TRIGGER))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChange(5, "WAITING", "UNKNOWN", true, null, null, "[]"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void collectedFilesAreStoredExactlyWhenCollected() {
        assertThatCode(() -> insertChange(1, "COMPLETED", "FEATURE", false, "COLLECTED", ONE_FILE, "[]"))
                .doesNotThrowAnyException();
        assertThatCode(() -> insertChange(2, "COMPLETED", "FEATURE", true, "UNAVAILABLE", null,
                "[{\"type\":\"CHANGED_FILES_UNAVAILABLE\",\"detail\":null}]"))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> insertChange(3, "COMPLETED", "FEATURE", false, "COLLECTED", null, "[]"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChange(4, "COMPLETED", "FEATURE", true, "UNAVAILABLE", ONE_FILE, "[]"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChange(5, "COMPLETED", "FEATURE", false, null, ONE_FILE, "[]"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChange(6, "COMPLETED", "FEATURE", false, "COLLECTED", "{}", "[]"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void reviewTriggersAlwaysRequireReviewUntilAPersonReviews() {
        assertThatCode(() -> insertChange(1, "COMPLETED", "FEATURE", true, "COLLECTED", ONE_FILE, SENSITIVE_TRIGGER))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> insertChange(2, "COMPLETED", "FEATURE", false, "COLLECTED", ONE_FILE, SENSITIVE_TRIGGER))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChange(3, "COMPLETED", "FEATURE", true, "COLLECTED", ONE_FILE, "{}"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void processingJobsStayInsideTheirChangesTenantAndState() {
        UUID change = insertChange(1, "PROCESSING", "UNKNOWN", true, null, null, "[]");
        UUID otherOrganization = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO organizations (id, name, created_at) VALUES (?, 'Other', now())", otherOrganization);

        assertThatThrownBy(() -> insertJob(change, otherOrganization, "PENDING", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertJob(change, organization, "COMPLETED", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertJob(change, organization, "ENRICHING", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertJob(change, organization, "FAILED", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertJob(change, organization, "ENRICHING", Instant.now(), null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> insertJob(change, organization, "PENDING", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anAccessTokenIsStoredWhole() {
        assertThatThrownBy(() -> insertIntegration(new byte[12], null, Instant.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertIntegration(new byte[12], new byte[32], null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertIntegration(new byte[8], new byte[32], Instant.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertIntegration(new byte[12], new byte[32], Instant.now()))
                .doesNotThrowAnyException();
    }

    private UUID insertChange(
            int number,
            String processingStatus,
            String category,
            boolean needsReview,
            String changedFileStatus,
            String changedFiles,
            String reviewTriggers
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO changes
                            (id, organization_id, project_id, pull_request_number, title, author_login, labels,
                             target_branch, merge_commit_sha, merged_at, url, delivery_id, received_at,
                             category, breaking, needs_review, classification_reasons,
                             classification_source, ai_status, processing_status, changed_file_status,
                             changed_files, review_triggers)
                        VALUES (?, ?, ?, ?, 'Title', 'octocat', '{}', 'main',
                                '0123456789abcdef0123456789abcdef01234567', now(),
                                'https://github.com/acme/releaseflow/pull/1', ?, now(),
                                ?, false, ?, '{"Seeded"}', 'RULES', 'NOT_REQUESTED', ?, ?,
                                CAST(? AS jsonb), CAST(? AS jsonb))
                        """,
                id,
                organization,
                project,
                number,
                UUID.randomUUID(),
                category,
                needsReview,
                processingStatus,
                changedFileStatus,
                changedFiles,
                reviewTriggers
        );
        return id;
    }

    private void insertJob(UUID changeId, UUID organizationId, String status, Instant claimedAt, Instant completedAt) {
        jdbcTemplate.update(
                """
                        INSERT INTO change_processing_jobs
                            (id, organization_id, project_id, change_id, status, attempts, next_attempt_at,
                             claimed_at, completed_at, created_at)
                        VALUES (?, ?, ?, ?, ?, 0, now(), ?, ?, now())
                        """,
                UUID.randomUUID(),
                organizationId,
                project,
                changeId,
                status,
                claimedAt == null ? null : Timestamp.from(claimedAt),
                completedAt == null ? null : Timestamp.from(completedAt)
        );
    }

    private void insertIntegration(byte[] tokenNonce, byte[] tokenCiphertext, Instant tokenUpdatedAt) {
        jdbcTemplate.update("DELETE FROM github_integrations");
        jdbcTemplate.update(
                """
                        INSERT INTO github_integrations
                            (id, organization_id, project_id, repository_owner, repository_name, webhook_id,
                             secret_nonce, secret_ciphertext, created_at, token_nonce, token_ciphertext,
                             token_updated_at)
                        VALUES (?, ?, ?, 'acme', 'releaseflow', ?, ?, ?, now(), ?, ?, ?)
                        """,
                UUID.randomUUID(),
                organization,
                project,
                UUID.randomUUID(),
                new byte[12],
                new byte[32],
                tokenNonce,
                tokenCiphertext,
                tokenUpdatedAt == null ? null : Timestamp.from(tokenUpdatedAt)
        );
    }
}
