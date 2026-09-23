package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.hoangluongtran0309.releaseflow.support.TestChanges;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The V15 schema for context assessments and possible duplicates, and its migration. */
class ChangeSignalsDatabaseIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String SCHEMA = "v15_signals_check";
    private static final String SUMMARY =
            "'{\"whatChanged\":\"x\",\"whyChanged\":\"\",\"technicalDetail\":\"\",\"migrationStep\":\"\"}'";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void keepsAContextAssessmentWholeAndTiedToASuccessfulAiAnswer() {
        UUID organization = insertOrganization();
        UUID project = insertProject(organization);
        UUID change = TestChanges.insert(jdbcTemplate, organization, project, 1, "feat: a", "FEATURE", false, false, null);
        String assessed = "context_score = 30, context_status = 'INSUFFICIENT', context_reasons = '[\"DESCRIPTION_MISSING\"]'";

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE changes SET " + assessed + " WHERE id = ?", change))
                .isInstanceOf(DataIntegrityViolationException.class);
        TestChanges.summarize(jdbcTemplate, change, "Adds a.", "{}");
        assertThatCode(() -> jdbcTemplate.update("UPDATE changes SET " + assessed + " WHERE id = ?", change))
                .doesNotThrowAnyException();
        for (String broken : new String[]{
                "context_score = 101", "context_score = -1", "context_status = 'MAYBE'", "context_reasons = '{}'",
                "context_reasons = NULL", "context_score = NULL"}) {
            assertThatThrownBy(() -> jdbcTemplate.update("UPDATE changes SET " + broken + " WHERE id = ?", change))
                    .as(broken)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void keepsPossibleDuplicatesConsistent() {
        UUID organization = insertOrganization();
        UUID project = insertProject(organization);
        UUID decider = insertUser(organization);
        UUID later = TestChanges.insert(jdbcTemplate, organization, project, 1, "feat: a", "FEATURE", false, false, null);
        UUID earlier = TestChanges.insert(jdbcTemplate, organization, project, 2, "feat: a", "FEATURE", false, false, null);
        UUID elsewhere = TestChanges.insert(jdbcTemplate, organization, insertProject(organization), 3,
                "feat: a", "FEATURE", false, false, null);

        UUID candidate = insertCandidate(organization, project, later, earlier, 0.9);
        assertThatThrownBy(() -> insertCandidate(organization, project, later, earlier, 0.9))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCandidate(organization, project, later, later, 0.9))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCandidate(organization, project, earlier, later, 1.2))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCandidate(organization, project, later, elsewhere, 0.9))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE duplicate_candidates SET status = 'CONFIRMED' WHERE id = ?", candidate))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> jdbcTemplate.update(
                "UPDATE duplicate_candidates SET status = 'CONFIRMED', decided_by = ?, decider_name = 'Mai', "
                        + "decided_at = now() WHERE id = ?", decider, candidate))
                .doesNotThrowAnyException();

        jdbcTemplate.update("DELETE FROM changes WHERE id = ?", earlier);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM duplicate_candidates", Long.class)).isZero();
    }

    @Test
    void leavesChangesRecordedBeforeV15Unassessed() {
        try {
            migrate("14");
            UUID organization = UUID.randomUUID();
            UUID project = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO " + SCHEMA + ".organizations (id, name, created_at, output_language) "
                    + "VALUES (?, 'Old', now(), 'en')", organization);
            jdbcTemplate.update("INSERT INTO " + SCHEMA + ".projects (id, organization_id, name, created_at) "
                    + "VALUES (?, ?, 'Old', now())", project, organization);
            jdbcTemplate.update(
                    "INSERT INTO " + SCHEMA + """
                            .changes (id, organization_id, project_id, pull_request_number, title, author_login, labels,
                                target_branch, merge_commit_sha, merged_at, url, delivery_id, received_at,
                                category, category_display_name, category_group, breaking, needs_review,
                                classification_reasons, classification_source, ai_status, ai_provider, ai_model,
                                ai_attempted_at, processing_status, changed_file_status, changed_files, review_triggers,
                                neutral_summary, content_language)
                            VALUES (?, ?, ?, 1, 'feat: a', 'octocat', '{}', 'main', ?, now(),
                                'https://github.com/acme/app/pull/1', ?, now(), 'FEATURE', 'Feature', 'FEATURE', false,
                                false, '{"Seeded"}', 'AI', 'SUCCEEDED', 'openai', 'gpt-test', now(), 'COMPLETED',
                                'COLLECTED', '[]', '[]',
                            """ + SUMMARY + ", 'en')",
                    UUID.randomUUID(), organization, project, "0".repeat(40), UUID.randomUUID()
            );

            migrate("15");

            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + SCHEMA
                    + ".changes WHERE context_score IS NULL AND context_status IS NULL AND context_reasons IS NULL",
                    Long.class)).isEqualTo(1);
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    private UUID insertCandidate(UUID organization, UUID project, UUID change, UUID duplicateOf, double similarity) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO duplicate_candidates (id, organization_id, project_id, change_id, duplicate_of_id,
                            similarity, evidence, status, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, '{"title":1,"content":1,"paths":0}', 'OPEN', now())
                        """,
                id, organization, project, change, duplicateOf, similarity
        );
        return id;
    }

    private UUID insertOrganization() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO organizations (id, name, slug, created_at, output_language) VALUES (?, 'Org', 'org-' || SUBSTRING(gen_random_uuid()::text, 1, 8), now(), 'en')", id);
        return id;
    }

    private UUID insertProject(UUID organization) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO projects (id, organization_id, name, created_at) VALUES (?, ?, 'Project', now())",
                id, organization);
        return id;
    }

    private UUID insertUser(UUID organization) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO app_users (id, organization_id, email, password_hash, display_name, role, created_at)
                        VALUES (?, ?, ?, 'hash', 'Mai', 'MEMBER', now())
                        """,
                id, organization, id + "@example.com"
        );
        return id;
    }

    private void migrate(String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }
}
