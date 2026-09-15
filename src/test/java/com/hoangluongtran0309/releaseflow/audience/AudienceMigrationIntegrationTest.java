package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** V13 on data that existed before it, migrated in a schema of its own. */
class AudienceMigrationIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String SCHEMA = "v13_audience_check";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void seedsTheShippedAudiencesAndSendsApprovedReleasesBackToReview() {
        try {
            migrate("12");
            UUID english = UUID.randomUUID();
            UUID vietnamese = UUID.randomUUID();
            UUID release = UUID.randomUUID();
            // Triggers name their tables without a schema, so the rows are written with the
            // migrated schema first on the search path.
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbcTemplate.execute("SET LOCAL search_path TO " + SCHEMA);
                seedApprovedRelease(english, vietnamese, release);
            });

            migrate("13");

            assertThat(audiences(english)).containsExactlyElementsOf(presets("en"));
            assertThat(audiences(vietnamese)).containsExactlyElementsOf(presets("vi-VN"));
            assertThat(jdbcTemplate.queryForMap(
                    "SELECT status, approved_at, approved_by, approver_name FROM " + SCHEMA + ".releases WHERE id = ?", release
            )).containsEntry("status", "IN_REVIEW")
                    .containsEntry("approved_at", null)
                    .containsEntry("approved_by", null)
                    .containsEntry("approver_name", null);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM " + SCHEMA + ".release_change_reviews WHERE release_id = ?", Long.class, release
            )).isEqualTo(1);
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    private void seedApprovedRelease(UUID english, UUID vietnamese, UUID release) {
        jdbcTemplate.update("INSERT INTO organizations (id, name, created_at, output_language) VALUES (?, 'Acme', now(), 'en')",
                english);
        jdbcTemplate.update("INSERT INTO organizations (id, name, created_at, output_language) VALUES (?, 'Công ty', now(), 'vi-VN')",
                vietnamese);
        UUID project = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID change = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO projects (id, organization_id, name, created_at) VALUES (?, ?, 'App', now())",
                project, english);
        jdbcTemplate.update("""
                        INSERT INTO app_users (id, organization_id, email, password_hash, display_name, role, created_at)
                        VALUES (?, ?, 'owner@example.com', 'hash', 'Owner', 'ADMIN', now())
                        """, user, english);
        jdbcTemplate.update("""
                        INSERT INTO changes
                            (id, organization_id, project_id, pull_request_number, title, author_login, labels,
                             target_branch, merge_commit_sha, merged_at, url, delivery_id, received_at,
                             category, breaking, needs_review, classification_reasons, classification_source, ai_status,
                             processing_status, changed_file_status, changed_files, review_triggers)
                        VALUES (?, ?, ?, 1, 'feat: a', 'mai-dev', '{}', 'main', ?, now(),
                                'https://github.com/acme/app/pull/1', ?, now(), 'FEATURE', false, false, '{"Seeded"}',
                                'RULES', 'NOT_REQUESTED', 'COMPLETED', 'COLLECTED', '[]', '[]')
                        """, change, english, project, "0".repeat(40), UUID.randomUUID());
        jdbcTemplate.update("""
                        INSERT INTO releases (id, organization_id, project_id, version, status, created_at, updated_at)
                        VALUES (?, ?, ?, '1.0.0', 'DRAFT', now(), now())
                        """, release, english, project);
        jdbcTemplate.update("""
                        INSERT INTO release_changes (release_id, change_id, organization_id, project_id, added_at)
                        VALUES (?, ?, ?, ?, now())
                        """, release, change, english, project);
        jdbcTemplate.update("UPDATE releases SET status = 'IN_REVIEW' WHERE id = ?", release);
        jdbcTemplate.update("""
                        INSERT INTO release_change_reviews
                            (release_id, change_id, organization_id, project_id, action, reviewer_id, reviewer_name, decided_at)
                        VALUES (?, ?, ?, ?, 'APPROVE', ?, 'Owner', now())
                        """, release, change, english, project, user);
        jdbcTemplate.update("""
                        UPDATE releases SET status = 'APPROVED', approved_at = now(), approved_by = ?, approver_name = 'Owner'
                        WHERE id = ?
                        """, user, release);
    }

    private List<AudiencePresets.Preset> audiences(UUID organizationId) {
        return jdbcTemplate.query(
                "SELECT code, display_name, communication_intent, template_body FROM " + SCHEMA
                        + ".audience_definitions WHERE organization_id = ? AND preset ORDER BY code",
                (row, index) -> new AudiencePresets.Preset(
                        row.getString("code"),
                        row.getString("display_name"),
                        row.getString("communication_intent"),
                        row.getString("template_body")
                ),
                organizationId
        );
    }

    private static List<AudiencePresets.Preset> presets(String language) {
        return AudiencePresets.forLanguage(language).stream()
                .sorted((left, right) -> left.code().compareTo(right.code()))
                .toList();
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
