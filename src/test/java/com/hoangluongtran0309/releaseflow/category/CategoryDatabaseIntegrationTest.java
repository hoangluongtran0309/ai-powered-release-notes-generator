package com.hoangluongtran0309.releaseflow.category;

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

/** The V14 schema: its constraints, and the migration of data recorded before it. */
class CategoryDatabaseIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String SCHEMA = "v14_category_check";

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
    void seedsTheCatalogAndSnapshotsTheCategoryOfExistingChanges() {
        try {
            migrate("13");
            UUID organization = UUID.randomUUID();
            UUID project = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO " + SCHEMA + ".organizations (id, name, created_at, output_language) "
                    + "VALUES (?, 'Old', now(), 'en')", organization);
            jdbcTemplate.update("INSERT INTO " + SCHEMA + ".projects (id, organization_id, name, created_at) "
                    + "VALUES (?, ?, 'Old', now())", project, organization);
            insertOldChange(organization, project, 1, "PERFORMANCE", false);
            insertOldChange(organization, project, 2, "UNKNOWN", true);

            migrate("14");

            assertThat(jdbcTemplate.query(
                    "SELECT code, display_name, category_group, system_category FROM " + SCHEMA
                            + ".category_definitions WHERE organization_id = ? AND active ORDER BY code",
                    (row, index) -> new CategoryRef(row.getString("code"), row.getString("display_name"),
                            CategoryGroup.valueOf(row.getString("category_group"))),
                    organization
            )).containsExactlyElementsOf(CategoryService.DEFAULTS.stream()
                    .sorted((left, right) -> left.code().compareTo(right.code()))
                    .toList());
            assertThat(jdbcTemplate.queryForList("SELECT code FROM " + SCHEMA
                    + ".category_definitions WHERE system_category", String.class)).containsExactly("UNKNOWN");
            assertThat(jdbcTemplate.queryForList(
                    "SELECT category || '|' || category_display_name || '|' || category_group FROM " + SCHEMA
                            + ".changes ORDER BY pull_request_number", String.class
            )).containsExactly("PERFORMANCE|Performance|PERFORMANCE", "UNKNOWN|Unknown|OTHER");
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    @Test
    void keepsTheCatalogValid() {
        UUID organization = insertOrganization();
        insertCategory(organization, "SECURITY", "FIX", false, true);

        assertThatThrownBy(() -> insertCategory(organization, "SECURITY", "FIX", false, true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertCategory(insertOrganization(), "SECURITY", "FIX", false, true)).doesNotThrowAnyException();
        for (String code : new String[]{"security_2x", "9LIVES", "DOT.TED", ""}) {
            assertThatThrownBy(() -> insertCategory(organization, code, "FIX", false, true))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> insertCategory(organization, "LEGAL", "BREAKING", false, true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCategory(organization, "UNKNOWN", "OTHER", false, true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCategory(organization, "UNKNOWN", "FIX", true, true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCategory(organization, "UNKNOWN", "OTHER", true, false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCategory(organization, "LEGAL", "OTHER", true, true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void keepsTheCategorySnapshotAndSuggestionsOfChangesConsistent() {
        UUID organization = insertOrganization();
        UUID project = insertProject(organization);
        UUID decider = insertUser(organization);
        UUID change = TestChanges.insert(jdbcTemplate, organization, project, 1, "Rotate keys", "UNKNOWN", false, true, null);

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE changes SET category_group = 'FIX' WHERE id = ?", change))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE changes SET category = 'security' WHERE id = ?", change))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE changes SET classification_source = 'SUGGESTION' WHERE id = ?", change))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> jdbcTemplate.update(
                "UPDATE changes SET category = 'SECURITY', category_display_name = 'Security', category_group = 'FIX' WHERE id = ?",
                change
        )).doesNotThrowAnyException();

        UUID suggestion = insertSuggestion(organization, project, change);
        assertThatThrownBy(() -> insertSuggestion(organization, project, change))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE category_suggestions SET status = 'APPROVED' WHERE id = ?", suggestion))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE category_suggestions SET status = 'REJECTED', resolved_code = 'FIX', decided_at = now(), "
                        + "decided_by = ?, decider_name = 'Admin' WHERE id = ?", decider, suggestion))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID outsider = insertUser(insertOrganization());
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE category_suggestions SET status = 'REJECTED', decided_at = now(), decided_by = ?, "
                        + "decider_name = 'Outsider' WHERE id = ?", outsider, suggestion))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> jdbcTemplate.update(
                "UPDATE category_suggestions SET status = 'MAPPED', resolved_code = 'FIX', decided_at = now(), "
                        + "decided_by = ?, decider_name = 'Admin' WHERE id = ?", decider, suggestion))
                .doesNotThrowAnyException();

        jdbcTemplate.update("DELETE FROM changes WHERE id = ?", change);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM category_suggestions", Long.class)).isZero();
    }

    private void insertOldChange(UUID organization, UUID project, int number, String category, boolean needsReview) {
        jdbcTemplate.update(
                "INSERT INTO " + SCHEMA + """
                        .changes (id, organization_id, project_id, pull_request_number, title, author_login, labels,
                            target_branch, merge_commit_sha, merged_at, url, delivery_id, received_at,
                            category, breaking, needs_review, classification_reasons, classification_source, ai_status,
                            processing_status, changed_file_status, changed_files, review_triggers)
                        VALUES (?, ?, ?, ?, 'Old change', 'octocat', '{}', 'main', ?, now(),
                            'https://github.com/acme/app/pull/1', ?, now(), ?, false, ?, '{"Seeded"}', 'RULES',
                            'NOT_REQUESTED', 'COMPLETED', 'COLLECTED', '[]', '[]')
                        """,
                UUID.randomUUID(), organization, project, number, "%040d".formatted(number), UUID.randomUUID(),
                category, needsReview
        );
    }

    private void insertCategory(UUID organization, String code, String group, boolean system, boolean active) {
        jdbcTemplate.update(
                """
                        INSERT INTO category_definitions (id, organization_id, code, display_name, category_group,
                            system_category, active, created_at, updated_at)
                        VALUES (?, ?, ?, 'Name', ?, ?, ?, now(), now())
                        """,
                UUID.randomUUID(), organization, code, group, system, active
        );
    }

    private UUID insertSuggestion(UUID organization, UUID project, UUID change) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO category_suggestions (id, organization_id, project_id, change_id, proposed_code,
                            proposed_name, proposed_group, rationale, status, created_at)
                        VALUES (?, ?, ?, ?, 'SECURITY', 'Security', 'FIX', '', 'PENDING_REVIEW', now())
                        """,
                id, organization, project, change
        );
        return id;
    }

    private UUID insertOrganization() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO organizations (id, name, created_at, output_language) VALUES (?, 'Org', now(), 'en')", id);
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
                        VALUES (?, ?, ?, 'hash', 'Admin', 'ADMIN', now())
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
