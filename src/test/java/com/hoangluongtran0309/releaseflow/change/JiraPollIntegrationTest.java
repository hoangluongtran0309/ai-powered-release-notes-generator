package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.JiraStub;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A Jira project read on its own schedule, through the same job machinery as an import. */
class JiraPollIntegrationTest extends PostgreSqlIntegrationTest {

    private static final JiraStub JIRA = JiraStub.start();
    private static final String EMAIL = "release-bot@acme.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private SourceSyncWorker syncWorker;

    @Autowired
    private ChangeProcessingWorker changeWorker;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void jiraProperties(DynamicPropertyRegistry registry) {
        registry.add("releaseflow.jira.api-base-url", JIRA::baseUrl);
        registry.add("releaseflow.jira.timeout", () -> "PT2S");
    }

    @AfterAll
    static void stopStub() {
        JIRA.close();
    }

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        JIRA.reset();
        jdbcTemplate.update("DELETE FROM change_processing_jobs");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM source_sync_jobs");
        jdbcTemplate.update("DELETE FROM integration_sources");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void nothingIsReadBeforeThePollIsDue() throws Exception {
        connect("early@example.com");

        assertThat(syncWorker.processOne()).isFalse();

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM source_sync_jobs", Long.class)).isZero();
        assertThat(JIRA.requests()).isEmpty();
    }

    @Test
    void aDuePollReadsCompletedIssuesAndBooksTheNextRound() throws Exception {
        UUID sourceId = connect("owner@example.com");
        Instant cursor = Instant.now().minus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.MICROS);
        setSchedule(sourceId, cursor, Instant.now().minusSeconds(1));
        Instant resolved = cursor.plus(Duration.ofMinutes(10));
        JIRA.respondWithSearchPages(
                List.of(JiraStub.doneIssue("10001", "APP-123", "Export large tables", "Mai Tran", resolved, resolved)),
                List.of(JiraStub.doneIssue("10002", "APP-124", "Fix login", "Lan Pham", resolved, null))
        );

        Instant before = Instant.now();
        assertThat(syncWorker.processOne()).isTrue();

        Map<String, Object> job = jdbcTemplate.queryForMap("""
                SELECT job_type, status, window_start, window_end, requested_by, requester_name, item_limit,
                       imported_count
                FROM source_sync_jobs""");
        assertThat(job)
                .containsEntry("job_type", "JIRA_POLL")
                .containsEntry("status", "COMPLETED")
                .containsEntry("requested_by", null)
                .containsEntry("requester_name", null)
                .containsEntry("item_limit", Integer.MAX_VALUE)
                .containsEntry("imported_count", 2);
        // The window reaches back before the cursor, so an issue finished at the edge is not lost.
        assertThat(((Timestamp) job.get("window_start")).toInstant()).isEqualTo(cursor.minus(Duration.ofMinutes(10)));
        Instant windowEnd = ((Timestamp) job.get("window_end")).toInstant();
        assertThat(JIRA.searchRequests()).hasSize(2);
        assertThat(JIRA.searchRequests().get(1).query()).containsEntry("nextPageToken", "page-1");

        Map<String, Object> source = jdbcTemplate.queryForMap(
                "SELECT poll_cursor_at, next_poll_at, last_sync_at, connection_status FROM integration_sources");
        assertThat(((Timestamp) source.get("poll_cursor_at")).toInstant()).isEqualTo(windowEnd);
        assertThat(((Timestamp) source.get("next_poll_at")).toInstant())
                .isCloseTo(before.plus(Duration.ofMinutes(5)), within(Duration.ofSeconds(30)));
        assertThat(source.get("last_sync_at")).isNotNull();
        assertThat(source.get("connection_status")).isEqualTo("ACTIVE");

        List<Map<String, Object>> changes = jdbcTemplate.queryForList("""
                SELECT pull_request_number, external_id, title, author_login, url, source_type, merge_commit_sha,
                       target_branch, origin
                FROM changes ORDER BY pull_request_number""");
        assertThat(changes).hasSize(2);
        assertThat(changes.getFirst())
                .containsEntry("pull_request_number", 123)
                .containsEntry("external_id", "10001")
                .containsEntry("title", "APP-123: Export large tables")
                .containsEntry("author_login", "Mai Tran")
                .containsEntry("url", JiraStub.SITE + "/browse/APP-123")
                .containsEntry("source_type", "JIRA")
                .containsEntry("merge_commit_sha", null)
                .containsEntry("target_branch", null);
        assertThat(syncWorker.processOne()).as("the next round is not due yet").isFalse();
    }

    @Test
    void skipsAnIssueFinishedBeforeTheWindowAndRecordsAnOverlapOnce() throws Exception {
        UUID sourceId = connect("overlap@example.com");
        Instant cursor = Instant.now().minus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.MICROS);
        Instant longAgo = cursor.minus(Duration.ofHours(3));
        Instant recent = cursor.plus(Duration.ofMinutes(5));
        // Jira reports an old issue that was merely edited, and one finished in the window.
        JIRA.respondWithSearchPages(List.of(
                JiraStub.doneIssue("20001", "APP-1", "Old work", "Mai", recent, longAgo),
                JiraStub.doneIssue("20002", "APP-2", "New work", "Mai", recent, recent)
        ));

        setSchedule(sourceId, cursor, Instant.now().minusSeconds(1));
        syncWorker.processOne();
        assertThat(jdbcTemplate.queryForList("SELECT pull_request_number FROM changes", Integer.class))
                .containsExactly(2);

        // The next round overlaps the last one and sees the same issue again.
        jdbcTemplate.update("UPDATE integration_sources SET poll_cursor_at = ?, next_poll_at = now() - interval '1 second'",
                Timestamp.from(recent.plusSeconds(1)));
        syncWorker.processOne();

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM source_sync_jobs", Long.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList("SELECT imported_count FROM source_sync_jobs ORDER BY created_at",
                Integer.class)).containsExactly(1, 0);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM changes", Long.class)).isEqualTo(1);
    }

    @Test
    void aPollThatMustWaitKeepsItsCursorAndIsNeverScheduledTwice() throws Exception {
        UUID sourceId = connect("retry@example.com");
        Instant cursor = Instant.now().minus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.MICROS);
        setSchedule(sourceId, cursor, Instant.now().minusSeconds(1));
        JIRA.failSearch(503, Map.of(), 1);

        syncWorker.processOne();

        assertThat(jdbcTemplate.queryForMap("SELECT status, last_error FROM source_sync_jobs"))
                .containsEntry("status", "RETRY_SCHEDULED")
                .containsEntry("last_error", JiraPollReader.UNAVAILABLE);
        Map<String, Object> source = jdbcTemplate.queryForMap(
                "SELECT poll_cursor_at, connection_status, last_error_code FROM integration_sources");
        assertThat(((Timestamp) source.get("poll_cursor_at")).toInstant()).isEqualTo(cursor);
        assertThat(source).containsEntry("connection_status", "ERROR")
                .containsEntry("last_error_code", JiraPollReader.UNAVAILABLE);

        // Even with the schedule overdue, the waiting poll is the only one.
        jdbcTemplate.update("UPDATE integration_sources SET next_poll_at = now() - interval '1 second'");
        assertThat(syncWorker.processOne()).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM source_sync_jobs", Long.class)).isEqualTo(1);

        // The retry reads the same window, which ended when the poll was scheduled.
        Instant resolved = cursor.plus(Duration.ofMinutes(1));
        JIRA.respondWithSearchPages(List.of(
                JiraStub.doneIssue("30001", "APP-5", "Done later", "Mai", resolved, resolved)));
        jdbcTemplate.update("UPDATE source_sync_jobs SET next_attempt_at = now() - interval '1 second'");
        syncWorker.processOne();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM source_sync_jobs", String.class))
                .isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForMap("SELECT connection_status, last_error_code FROM integration_sources"))
                .containsEntry("connection_status", "ACTIVE")
                .containsEntry("last_error_code", null);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM changes", Long.class)).isEqualTo(1);
    }

    @Test
    void aRefusedPollFailsAndTriesAgainLaterFromTheSameCursor() throws Exception {
        UUID sourceId = connect("refused@example.com");
        Instant cursor = Instant.now().minus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.MICROS);
        setSchedule(sourceId, cursor, Instant.now().minusSeconds(1));
        JIRA.failSearch(401, Map.of(), 1);

        Instant before = Instant.now();
        syncWorker.processOne();

        assertThat(jdbcTemplate.queryForMap("SELECT status, last_error FROM source_sync_jobs"))
                .containsEntry("status", "FAILED")
                .containsEntry("last_error", SourceSyncWorker.ACCESS_REJECTED);
        Map<String, Object> source = jdbcTemplate.queryForMap(
                "SELECT poll_cursor_at, next_poll_at, connection_status FROM integration_sources");
        assertThat(((Timestamp) source.get("poll_cursor_at")).toInstant()).isEqualTo(cursor);
        assertThat(((Timestamp) source.get("next_poll_at")).toInstant())
                .isCloseTo(before.plus(SourceSyncWorker.POLL_RETRY_AFTER_FAILURE), within(Duration.ofSeconds(30)));
        assertThat(source.get("connection_status")).isEqualTo("ERROR");

        // Once due again, the schedule makes a fresh poll over the same window start.
        jdbcTemplate.update("UPDATE integration_sources SET next_poll_at = now() - interval '1 second'");
        syncWorker.processOne();
        assertThat(jdbcTemplate.queryForList(
                "SELECT window_start FROM source_sync_jobs ORDER BY created_at", Timestamp.class))
                .extracting(Timestamp::toInstant)
                .containsOnly(cursor.minus(Duration.ofMinutes(10)));
    }

    // The key leads the title, so a title type never matches and the rules leave it Unknown.
    @Test
    void aPolledIssueIsProcessedWithoutFilesOrLinks() throws Exception {
        UUID sourceId = connect("process@example.com");
        Instant cursor = Instant.now().minus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.MICROS);
        setSchedule(sourceId, cursor, Instant.now().minusSeconds(1));
        Instant resolved = cursor.plus(Duration.ofMinutes(1));
        JIRA.respondWithSearchPages(List.of(
                JiraStub.doneIssue("40001", "APP-9", "feat: add CSV export", "Mai", resolved, resolved)));
        syncWorker.processOne();
        JIRA.reset();

        assertThat(changeWorker.processOne()).isTrue();

        assertThat(jdbcTemplate.queryForMap("""
                SELECT processing_status, changed_file_status, linked_context_status, linked_issues, category,
                       review_triggers::text AS review_triggers
                FROM changes"""))
                .containsEntry("processing_status", "COMPLETED")
                .containsEntry("changed_file_status", "NOT_SUPPORTED")
                .containsEntry("linked_context_status", "NOT_SUPPORTED")
                .containsEntry("linked_issues", null)
                .containsEntry("category", "UNKNOWN")
                .containsEntry("review_triggers", "[]");
        assertThat(JIRA.requests()).as("the poll already read the issue").isEmpty();
    }

    private void setSchedule(UUID sourceId, Instant cursor, Instant nextPoll) {
        jdbcTemplate.update("UPDATE integration_sources SET poll_cursor_at = ?, next_poll_at = ? WHERE id = ?",
                Timestamp.from(cursor), Timestamp.from(nextPoll), sourceId);
    }

    private UUID connect(String email) throws Exception {
        RegistrationRequest registration = new RegistrationRequest();
        registration.setOrganizationName(email);
        registration.setDisplayName("Owner");
        registration.setEmail(email);
        registration.setPassword("owner-password");
        registrationService.register(registration);
        MvcResult login = mockMvc.perform(post("/login").with(csrf())
                        .param("email", email).param("password", "owner-password"))
                .andExpect(status().isFound())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        MvcResult project = mockMvc.perform(post("/api/projects").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ReleaseFlow\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID projectId = UUID.fromString(JsonPath.read(project.getResponse().getContentAsString(), "$.id"));
        MvcResult source = mockMvc.perform(post("/api/projects/{projectId}/sources", projectId)
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"JIRA","siteUrl":"%s","projectKey":"APP","accountEmail":"%s",\
                                "apiToken":"poll-token"}""".formatted(JiraStub.SITE, EMAIL)))
                .andExpect(status().isCreated())
                .andReturn();
        JIRA.reset();
        return UUID.fromString(JsonPath.read(source.getResponse().getContentAsString(), "$.id"));
    }
}
