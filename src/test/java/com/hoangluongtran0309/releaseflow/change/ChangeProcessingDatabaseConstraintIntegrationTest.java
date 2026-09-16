package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.hoangluongtran0309.releaseflow.support.TestChanges;
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
        jdbcTemplate.update("INSERT INTO organizations (id, name, created_at, output_language) VALUES (?, 'Acme', now(), 'en')", organization);
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
        jdbcTemplate.update("DELETE FROM integration_sources");
        jdbcTemplate.update("DELETE FROM projects");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void aProcessingChangeStaysUnsettledAndUnclassified() {
        assertThatCode(() -> insertChange(1, "PROCESSING", "UNKNOWN", true, null, null, "[]"))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> insertChange(2, "PROCESSING", "FEATURE", false, null, null, "[]"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Files are recorded before the AI call (V11), so a Processing change may carry them.
        assertThatCode(() -> insertChange(3, "PROCESSING", "UNKNOWN", true, "COLLECTED", ONE_FILE, "[]"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> insertChange(6, "PROCESSING", "FEATURE", true, null, null, "[]"))
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
        jdbcTemplate.update("INSERT INTO organizations (id, name, created_at, output_language) VALUES (?, 'Other', now(), 'en')", otherOrganization);

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
    void aiResultsAreRecordedConsistently() {
        String summary = "{\"whatChanged\":\"Adds export.\",\"whyChanged\":\"\",\"technicalDetail\":\"\",\"migrationStep\":\"\"}";
        assertThatCode(() -> insertAiChange(1, "COMPLETED", "RULES", "SUCCEEDED", "openai", "gpt", null, summary, "vi", false))
                .doesNotThrowAnyException();
        assertThatCode(() -> insertAiChange(2, "COMPLETED", "AI", "SUCCEEDED", "anthropic", "claude", null, summary, "en", false))
                .doesNotThrowAnyException();
        assertThatCode(() -> insertAiChange(3, "COMPLETED", "RULES", "FAILED", "deepseek", "ds", "DeepSeek returned HTTP 500.", null, null, true))
                .doesNotThrowAnyException();

        // A summary needs a successful attempt and a language.
        assertThatThrownBy(() -> insertAiChange(4, "COMPLETED", "RULES", "FAILED", "openai", "gpt", "Failed.", summary, "en", true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAiChange(5, "COMPLETED", "RULES", "SUCCEEDED", "openai", "gpt", null, summary, null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
        // The provider is recorded exactly when the AI was asked.
        assertThatThrownBy(() -> insertAiChange(6, "COMPLETED", "RULES", "SUCCEEDED", null, "gpt", null, null, null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAiChange(7, "COMPLETED", "RULES", "SUCCEEDED", "gemini", "g", null, null, null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
        // An AI category requires a successful attempt.
        assertThatThrownBy(() -> insertAiChange(8, "COMPLETED", "AI", "FAILED", "openai", "gpt", "Failed.", null, null, true))
                .isInstanceOf(DataIntegrityViolationException.class);
        // A Processing change has not been sent to AI yet.
        assertThatThrownBy(() -> insertAiChange(9, "PROCESSING", "RULES", "SUCCEEDED", "openai", "gpt", null, summary, "en", true))
                .isInstanceOf(DataIntegrityViolationException.class);
        // The Organization's output language cannot be blank.
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE organizations SET output_language = ' ' WHERE id = ?", organization))
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
                             category, category_display_name, category_group, breaking, needs_review, classification_reasons,
                             classification_source, ai_status, processing_status, changed_file_status,
                             changed_files, review_triggers)
                        VALUES (?, ?, ?, ?, 'Title', 'octocat', '{}', 'main',
                                '0123456789abcdef0123456789abcdef01234567', now(),
                                'https://github.com/acme/releaseflow/pull/1', ?, now(),
                                ?, ?, ?, false, ?, '{"Seeded"}', 'RULES', 'NOT_REQUESTED', ?, ?,
                                CAST(? AS jsonb), CAST(? AS jsonb))
                        """,
                id,
                organization,
                project,
                number,
                UUID.randomUUID(),
                category,
                TestChanges.displayName(category),
                TestChanges.group(category),
                needsReview,
                processingStatus,
                changedFileStatus,
                changedFiles,
                reviewTriggers
        );
        return id;
    }

    private void insertAiChange(
            int number,
            String processingStatus,
            String source,
            String aiStatus,
            String provider,
            String model,
            String failure,
            String summary,
            String language,
            boolean needsReview
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO changes
                            (id, organization_id, project_id, pull_request_number, title, author_login, labels,
                             target_branch, merge_commit_sha, merged_at, url, delivery_id, received_at,
                             category, category_display_name, category_group, breaking, needs_review, classification_reasons,
                             classification_source, ai_status, ai_provider, ai_model, ai_failure, ai_attempted_at,
                             processing_status, changed_file_status, changed_files, review_triggers,
                             neutral_summary, content_language)
                        VALUES (?, ?, ?, ?, 'Title', 'octocat', '{}', 'main',
                                '0123456789abcdef0123456789abcdef01234567', now(),
                                'https://github.com/acme/releaseflow/pull/1', ?, now(),
                                'FEATURE', 'Feature', 'FEATURE', false, ?, '{"Seeded"}', ?, ?, ?, ?, ?, now(), ?, 'COLLECTED',
                                CAST(? AS jsonb), '[]', CAST(? AS jsonb), ?)
                        """,
                UUID.randomUUID(),
                organization,
                project,
                number,
                UUID.randomUUID(),
                needsReview,
                source,
                aiStatus,
                provider,
                model,
                failure,
                processingStatus,
                ONE_FILE,
                summary,
                language
        );
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
        jdbcTemplate.update("DELETE FROM integration_sources");
        jdbcTemplate.update(
                """
                        INSERT INTO integration_sources
                            (id, organization_id, project_id, source_type, external_project_key, repository_owner,
                             repository_name, webhook_auth_mode, webhook_id, secret_nonce, secret_ciphertext,
                             created_at, token_nonce, token_ciphertext, token_updated_at)
                        VALUES (?, ?, ?, 'GITHUB', 'acme/releaseflow', 'acme', 'releaseflow', 'GITHUB_HMAC', ?, ?, ?,
                                now(), ?, ?, ?)
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
