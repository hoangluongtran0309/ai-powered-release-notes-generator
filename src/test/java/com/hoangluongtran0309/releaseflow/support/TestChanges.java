package com.hoangluongtran0309.releaseflow.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Inserts classified changes directly, for tests of capabilities that consume changes.
 * A settled breaking change is recorded as reviewed by the given user, as the schema requires.
 */
public final class TestChanges {

    private TestChanges() {
    }

    public static UUID insert(
            JdbcTemplate jdbcTemplate,
            UUID organizationId,
            UUID projectId,
            int number,
            String title,
            String category,
            boolean breaking,
            boolean needsReview,
            UUID reviewerId
    ) {
        UUID id = UUID.randomUUID();
        boolean reviewed = !needsReview && (breaking || reviewerId != null);
        jdbcTemplate.update(
                """
                        INSERT INTO changes
                            (id, organization_id, project_id, pull_request_number, title, author_login, labels,
                             target_branch, merge_commit_sha, merged_at, url, delivery_id, received_at,
                             category, breaking, needs_review, classification_reasons,
                             classification_source, ai_status, reviewed_by, reviewer_name, reviewed_at,
                             processing_status, changed_file_status, changed_files, review_triggers)
                        VALUES (?, ?, ?, ?, ?, 'mai-dev', '{}', 'main', ?, ?, ?, ?, now(),
                                ?, ?, ?, '{"Seeded for a test"}', 'RULES', 'NOT_REQUESTED', ?, ?, ?,
                                'COMPLETED', 'COLLECTED', '[]', '[]')
                        """,
                id,
                organizationId,
                projectId,
                number,
                title,
                "%040d".formatted(number),
                Timestamp.from(Instant.parse("2026-09-01T10:00:00Z").plusSeconds(number * 60L)),
                "https://github.com/acme/releaseflow/pull/" + number,
                UUID.randomUUID(),
                category,
                breaking,
                needsReview,
                reviewed ? reviewerId : null,
                reviewed ? "Reviewer" : null,
                reviewed ? Timestamp.from(Instant.now()) : null
        );
        return id;
    }

    /**
     * Records a successful AI summary with the given narratives (a JSON object keyed by
     * audience code), as the worker would.
     */
    public static void summarize(JdbcTemplate jdbcTemplate, UUID changeId, String whatChanged, String narrativesJson) {
        jdbcTemplate.update(
                """
                        UPDATE changes
                        SET ai_status = 'SUCCEEDED', ai_provider = 'openai', ai_model = 'gpt-test', ai_attempted_at = now(),
                            neutral_summary = jsonb_build_object('whatChanged', ?::text, 'whyChanged', 'Users asked.',
                                'technicalDetail', 'Streams rows.', 'migrationStep', ''),
                            audience_narratives = ?::jsonb,
                            content_language = 'en'
                        WHERE id = ?
                        """,
                whatChanged,
                narrativesJson,
                changeId
        );
    }
}
