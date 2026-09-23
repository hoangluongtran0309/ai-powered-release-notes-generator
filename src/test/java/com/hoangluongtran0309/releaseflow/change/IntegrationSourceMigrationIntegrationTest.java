package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V18 turns GitHub integrations into integration sources without touching their secrets. */
class IntegrationSourceMigrationIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String SCHEMA = "v18_sources_check";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    void keepsEveryIntegrationAndIdentifiesItsChanges() {
        try {
            migrate("17");
            UUID organization = UUID.randomUUID();
            UUID project = UUID.randomUUID();
            UUID integration = UUID.randomUUID();
            UUID webhook = UUID.randomUUID();
            UUID change = UUID.randomUUID();
            byte[] nonce = new byte[12];
            byte[] ciphertext = "a secret sealed with AAD".getBytes();
            jdbcTemplate.update("INSERT INTO " + SCHEMA + ".organizations (id, name, created_at, output_language) "
                    + "VALUES (?, 'Old', now(), 'en')", organization);
            jdbcTemplate.update("INSERT INTO " + SCHEMA + ".projects (id, organization_id, name, created_at) "
                    + "VALUES (?, ?, 'Old', now())", project, organization);
            jdbcTemplate.update("INSERT INTO " + SCHEMA + ".github_integrations (id, organization_id, project_id, "
                            + "repository_owner, repository_name, webhook_id, secret_nonce, secret_ciphertext, created_at) "
                            + "VALUES (?, ?, ?, 'acme', 'releaseflow', ?, ?, ?, now())",
                    integration, organization, project, webhook, nonce, ciphertext);
            jdbcTemplate.update(
                    "INSERT INTO " + SCHEMA + """
                            .changes (id, organization_id, project_id, pull_request_number, title, author_login, labels,
                                target_branch, merge_commit_sha, merged_at, url, delivery_id, received_at,
                                category, category_display_name, category_group, breaking, needs_review,
                                classification_reasons, classification_source, ai_status, processing_status,
                                changed_file_status, changed_files, review_triggers)
                            VALUES (?, ?, ?, 42, 'feat: a', 'octocat', '{}', 'main', ?, now(),
                                'https://github.com/acme/releaseflow/pull/42', ?, now(), 'FEATURE', 'Feature', 'FEATURE',
                                false, false, '{"Seeded"}', 'RULES', 'NOT_REQUESTED', 'COMPLETED', 'COLLECTED', '[]', '[]')
                            """,
                    change, organization, project, "0".repeat(40), UUID.randomUUID()
            );

            migrate("18");

            Map<String, Object> source = jdbcTemplate.queryForMap("SELECT * FROM " + SCHEMA + ".integration_sources");
            assertThat(source)
                    .containsEntry("id", integration)
                    .containsEntry("webhook_id", webhook)
                    .containsEntry("source_type", "GITHUB")
                    .containsEntry("external_project_key", "acme/releaseflow")
                    .containsEntry("connection_status", "ACTIVE");
            assertThat((byte[]) source.get("secret_nonce")).isEqualTo(nonce);
            assertThat((byte[]) source.get("secret_ciphertext")).isEqualTo(ciphertext);
            assertThat(jdbcTemplate.queryForMap("SELECT source_id, external_id, origin FROM " + SCHEMA + ".changes"))
                    .containsEntry("source_id", integration)
                    .containsEntry("external_id", "42")
                    .containsEntry("origin", "WEBHOOK");

            // A second repository for the same Project, whose pull request #42 is another change.
            UUID second = UUID.randomUUID();
            assertThatCode(() -> jdbcTemplate.update("INSERT INTO " + SCHEMA + ".integration_sources (id, organization_id, "
                            + "project_id, source_type, external_project_key, repository_owner, repository_name, webhook_id, "
                            + "secret_nonce, secret_ciphertext, created_at) VALUES (?, ?, ?, 'GITHUB', 'acme/web', 'acme', "
                            + "'web', ?, ?, ?, now())",
                    second, organization, project, UUID.randomUUID(), nonce, ciphertext)).doesNotThrowAnyException();
            assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO " + SCHEMA + ".integration_sources (id, "
                            + "organization_id, project_id, source_type, external_project_key, repository_owner, "
                            + "repository_name, webhook_id, secret_nonce, secret_ciphertext, created_at) VALUES (?, ?, ?, "
                            + "'GITHUB', 'acme/other', 'acme', 'releaseflow', ?, ?, ?, now())",
                    UUID.randomUUID(), organization, project, UUID.randomUUID(), nonce, ciphertext))
                    .as("the key of a GitHub source is its repository")
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO " + SCHEMA + ".integration_sources (id, "
                            + "organization_id, project_id, source_type, external_project_key, repository_owner, "
                            + "repository_name, webhook_id, secret_nonce, secret_ciphertext, created_at) VALUES (?, ?, ?, "
                            + "'GITHUB', 'acme/web', 'acme', 'web', ?, ?, ?, now())",
                    UUID.randomUUID(), organization, project, UUID.randomUUID(), nonce, ciphertext))
                    .as("a repository once per Organization")
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
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
