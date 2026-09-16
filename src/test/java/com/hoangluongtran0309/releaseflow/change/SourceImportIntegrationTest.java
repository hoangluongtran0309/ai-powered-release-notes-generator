package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.source.ChangedFiles;
import com.hoangluongtran0309.releaseflow.support.GitHubStub;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.hoangluongtran0309.releaseflow.support.GitHubStub.closedPullRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Importing a repository's merged pull requests from a stand-in for the GitHub API. */
class SourceImportIntegrationTest extends PostgreSqlIntegrationTest {

    private static final GitHubStub GITHUB = GitHubStub.start();
    private static final String TOKEN = "github_pat_import-test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SourceImportWorker importWorker;

    @Autowired
    private ChangeProcessingWorker changeWorker;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void gitHubProperties(DynamicPropertyRegistry registry) {
        registry.add("releaseflow.github.api-base-url", GITHUB::baseUrl);
        registry.add("releaseflow.github.timeout", () -> "PT2S");
    }

    @AfterAll
    static void stopStub() {
        GITHUB.close();
    }

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        GITHUB.reset();
        jdbcTemplate.execute("TRUNCATE release_audience_notes, release_change_reviews, release_notes, release_changes, releases");
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
    void importsTheMergedPullRequestsOfTheLastNinetyDaysAcrossPages() throws Exception {
        Admin admin = connect("owner@example.com");
        Instant now = Instant.now();
        List<Map<String, Object>> closed = new ArrayList<>();
        for (int number = 1; number <= 120; number++) {
            Instant mergedAt = now.minus(Duration.ofDays(1)).minus(Duration.ofMinutes(number));
            closed.add(closedPullRequest(number, mergedAt, mergedAt.plusSeconds(60)));
        }
        closed.add(closedPullRequest(121, null, now.minus(Duration.ofHours(2))));
        closed.add(closedPullRequest(122, now.minus(Duration.ofDays(100)), now.minus(Duration.ofDays(1))));
        closed.add(closedPullRequest(123, now.minus(Duration.ofDays(120)), now.minus(Duration.ofDays(120))));
        closed.add(closedPullRequest(124, now.minus(Duration.ofDays(130)), now.minus(Duration.ofDays(130))));
        GITHUB.respondWithPullRequests(closed);

        startImport(admin, admin.sourceId())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.deliveryMechanism").value("WEBHOOK"))
                .andExpect(jsonPath("$.requesterName").value("Owner"));
        assertThat(importWorker.processOne()).isTrue();
        assertThat(importWorker.processOne()).isFalse();

        assertThat(GITHUB.listRequests()).hasSize(2).allSatisfy(request -> {
            assertThat(request.path()).isEqualTo("/repos/acme/releaseflow/pulls");
            assertThat(request.query()).contains("state=closed", "sort=updated", "direction=desc", "per_page=100");
            assertThat(request.authorization()).isEqualTo("Bearer " + TOKEN);
        });
        assertThat(GITHUB.listRequests()).extracting(GitHubStub.RecordedRequest::query)
                .anySatisfy(query -> assertThat(query).contains("&page=1"))
                .anySatisfy(query -> assertThat(query).contains("&page=2"));
        mockMvc.perform(get("/api/projects/{projectId}/imports", admin.projectId()).session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].scannedCount").value(123))
                .andExpect(jsonPath("$[0].importedCount").value(120))
                .andExpect(jsonPath("$[0].lastSyncAt").isNotEmpty())
                .andExpect(jsonPath("$[0].canResumeImport").value(false));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM changes WHERE origin = 'IMPORT' AND delivery_id IS NULL AND source_id = ?",
                Long.class, admin.sourceId())).isEqualTo(120);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM change_processing_jobs", Long.class)).isEqualTo(120);
        assertThat(jdbcTemplate.queryForList("SELECT pull_request_number FROM changes", Integer.class))
                .doesNotContain(121, 122, 123, 124);

        // Imported changes go through the same processing as webhook deliveries.
        GITHUB.respondWithFiles("src/main/java/Export.java");
        assertThat(changeWorker.processOne()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM changes WHERE processing_status = 'COMPLETED'", Long.class)).isOne();
        mockMvc.perform(get("/changes").param("project", admin.projectId().toString()).session(admin.session()))
                .andExpect(content().string(containsString("History import")))
                .andExpect(content().string(containsString("123 scanned, 120 imported")))
                .andExpect(content().string(containsString(">Imported</span>")));
    }

    @Test
    void stopsAtItsLimitAndResumesWhereItStopped() throws Exception {
        Admin admin = connect("owner@example.com");
        Instant now = Instant.now();
        List<Map<String, Object>> closed = new ArrayList<>();
        for (int number = 1; number <= 5; number++) {
            Instant mergedAt = now.minus(Duration.ofDays(number));
            closed.add(closedPullRequest(number, mergedAt, mergedAt));
        }
        GITHUB.respondWithPullRequests(closed);
        startImport(admin, admin.sourceId()).andExpect(status().isAccepted());
        jdbcTemplate.update("UPDATE source_sync_jobs SET item_limit = 2");

        importWorker.processOne();
        assertThat(jdbcTemplate.queryForMap("SELECT status, provider_cursor, imported_count FROM source_sync_jobs"))
                .containsEntry("status", "PARTIAL")
                .containsEntry("provider_cursor", "1:2")
                .containsEntry("imported_count", 2);
        mockMvc.perform(get("/api/projects/{projectId}/imports", admin.projectId()).session(admin.session()))
                .andExpect(jsonPath("$[0].canResumeImport").value(true));
        mockMvc.perform(get("/changes").param("project", admin.projectId().toString()).session(admin.session()))
                .andExpect(content().string(containsString("Stopped at the limit")))
                .andExpect(content().string(containsString(">Resume</button>")));

        resume(admin, admin.sourceId()).andExpect(status().isAccepted()).andExpect(jsonPath("$.importedCount").value(0));
        importWorker.processOne();
        assertThat(jdbcTemplate.queryForMap("SELECT status, provider_cursor, imported_count FROM source_sync_jobs"))
                .containsEntry("status", "PARTIAL")
                .containsEntry("provider_cursor", "1:4");
        resume(admin, admin.sourceId()).andExpect(status().isAccepted());
        importWorker.processOne();
        assertThat(jdbcTemplate.queryForMap("SELECT status, imported_count, scanned_count FROM source_sync_jobs"))
                .containsEntry("status", "COMPLETED")
                .containsEntry("imported_count", 1)
                .containsEntry("scanned_count", 5);
        assertThat(jdbcTemplate.queryForList("SELECT pull_request_number FROM changes ORDER BY pull_request_number",
                Integer.class)).containsExactly(1, 2, 3, 4, 5);
        resume(admin, admin.sourceId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("source_import_not_resumable"));
    }

    @Test
    void waitsAsLongAsGitHubAsksAndGivesUpAfterFiveAttempts() throws Exception {
        Admin admin = connect("owner@example.com");
        GITHUB.respondWithPullRequests(List.of(closedPullRequest(1, Instant.now(), Instant.now())));
        startImport(admin, admin.sourceId()).andExpect(status().isAccepted());

        GITHUB.failPullRequestList(403, Map.of("Retry-After", "120"), 1);
        Instant before = Instant.now();
        importWorker.processOne();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM source_sync_jobs", String.class))
                .isEqualTo("RETRY_SCHEDULED");
        assertThat(nextAttemptAt()).isCloseTo(before.plusSeconds(120), within(Duration.ofSeconds(10)));
        assertThat(importWorker.processOne()).as("not due yet").isFalse();

        makeDue();
        GITHUB.failPullRequestList(429, Map.of("Retry-After", "7200"), 1);
        importWorker.processOne();
        assertThat(nextAttemptAt()).as("capped at an hour")
                .isCloseTo(Instant.now().plus(Duration.ofHours(1)), within(Duration.ofSeconds(10)));

        makeDue();
        long reset = Instant.now().plusSeconds(60).getEpochSecond();
        GITHUB.failPullRequestList(403, Map.of("x-ratelimit-remaining", "0", "x-ratelimit-reset", Long.toString(reset)), 1);
        importWorker.processOne();
        assertThat(nextAttemptAt()).isCloseTo(Instant.ofEpochSecond(reset), within(Duration.ofSeconds(10)));

        GITHUB.failPullRequestList(502, Map.of(), 2);
        makeDue();
        importWorker.processOne();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM source_sync_jobs", String.class))
                .isEqualTo("RETRY_SCHEDULED");
        makeDue();
        importWorker.processOne();
        assertThat(jdbcTemplate.queryForMap("SELECT status, attempts, last_error FROM source_sync_jobs"))
                .containsEntry("status", "FAILED")
                .containsEntry("attempts", SourceImportWorker.MAX_ATTEMPTS)
                .containsEntry("last_error", ChangedFiles.GITHUB_UNAVAILABLE);
        mockMvc.perform(get("/api/projects/{projectId}/imports", admin.projectId()).session(admin.session()))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].lastErrorCode").value("github_unavailable"))
                .andExpect(jsonPath("$[0].canResumeImport").value(true));

        // Resuming a failed import continues from its cursor with a fresh budget.
        resume(admin, admin.sourceId()).andExpect(status().isAccepted());
        importWorker.processOne();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM source_sync_jobs", String.class)).isEqualTo("COMPLETED");
    }

    @Test
    void aRefusedTokenFailsTheImportAndMarksTheSource() throws Exception {
        Admin admin = connect("owner@example.com");
        startImport(admin, admin.sourceId()).andExpect(status().isAccepted());
        GITHUB.failPullRequestList(401, Map.of(), 1);

        importWorker.processOne();

        assertThat(jdbcTemplate.queryForMap("SELECT status, attempts, last_error FROM source_sync_jobs"))
                .containsEntry("status", "FAILED")
                .containsEntry("attempts", 1)
                .containsEntry("last_error", SourceImportWorker.ACCESS_REJECTED);
        mockMvc.perform(get("/api/projects/{projectId}/sources", admin.projectId()).session(admin.session()))
                .andExpect(jsonPath("$[0].connectionStatus").value("ERROR"))
                .andExpect(jsonPath("$[0].lastErrorCode").value("access_rejected"));
        mockMvc.perform(get("/projects").session(admin.session()))
                .andExpect(content().string(containsString("Connection failing")));

        // A token GitHub accepts puts the source back in order.
        setToken(admin, admin.sourceId());
        mockMvc.perform(get("/api/projects/{projectId}/sources", admin.projectId()).session(admin.session()))
                .andExpect(jsonPath("$[0].connectionStatus").value("ACTIVE"));
    }

    @Test
    void onlyOneImportOfASourceIsUnderWayAtATime() throws Exception {
        Admin admin = connect("owner@example.com");

        startImport(admin, admin.sourceId()).andExpect(status().isAccepted());
        startImport(admin, admin.sourceId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("source_sync_in_progress"));
        resume(admin, admin.sourceId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("source_import_not_resumable"));
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO source_sync_jobs (id, organization_id, project_id, source_id, job_type, status, window_start,
                    window_end, provider_cursor, scanned_count, imported_count, item_limit, attempts, next_attempt_at,
                    requested_by, requester_name, created_at)
                SELECT gen_random_uuid(), organization_id, project_id, source_id, job_type, 'RETRY_SCHEDULED',
                    window_start, window_end, '1:0', 0, 0, 500, 0, now(), requested_by, requester_name, now()
                FROM source_sync_jobs
                """))
                .isInstanceOf(DataIntegrityViolationException.class);

        importWorker.processOne();
        startImport(admin, admin.sourceId()).andExpect(status().isAccepted());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM source_sync_jobs", Long.class)).isEqualTo(2);
    }

    @Test
    void anImportNeverDuplicatesAWebhookDelivery() throws Exception {
        Admin admin = connect("owner@example.com");
        Instant now = Instant.now();
        deliver(admin, 7);
        GITHUB.respondWithPullRequests(List.of(
                closedPullRequest(7, now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(2))),
                closedPullRequest(8, now.minus(Duration.ofDays(3)), now.minus(Duration.ofDays(3)))
        ));

        startImport(admin, admin.sourceId()).andExpect(status().isAccepted());
        importWorker.processOne();

        assertThat(jdbcTemplate.queryForMap("SELECT scanned_count, imported_count FROM source_sync_jobs"))
                .containsEntry("scanned_count", 2)
                .containsEntry("imported_count", 1);
        assertThat(jdbcTemplate.queryForList("SELECT origin FROM changes ORDER BY pull_request_number", String.class))
                .containsExactly("WEBHOOK", "IMPORT");
        deliverExpecting(admin, 8, "duplicate");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM changes", Long.class)).isEqualTo(2);
    }

    @Test
    void twoRepositoriesOfAProjectKeepTheirOwnPullRequestNumbers() throws Exception {
        Admin admin = connect("owner@example.com");
        UUID web = createSource(admin, "releaseflow-web");
        setToken(admin, web);
        GITHUB.respondWithPullRequests(List.of(closedPullRequest(1, Instant.now(), Instant.now())));

        startImport(admin, admin.sourceId()).andExpect(status().isAccepted());
        startImport(admin, web).andExpect(status().isAccepted());
        importWorker.processOne();
        importWorker.processOne();

        assertThat(jdbcTemplate.queryForList("SELECT source_id FROM changes WHERE pull_request_number = 1", UUID.class))
                .containsExactlyInAnyOrder(admin.sourceId(), web);
        mockMvc.perform(get("/api/projects/{projectId}/imports", admin.projectId()).session(admin.session()))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].status").value(everyItem(is("COMPLETED"))));
    }

    @Test
    void anImportNeedsAnAccessTokenAndAnAdministrator() throws Exception {
        Admin admin = connect("owner@example.com");
        UUID withoutToken = createSource(admin, "releaseflow-docs");
        MockHttpSession member = member(admin.organizationId());
        Admin other = connect("other@example.com");

        startImport(admin, withoutToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("source_token_missing"));
        mockMvc.perform(get("/api/projects/{projectId}/imports", admin.projectId()).session(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(post("/api/projects/{projectId}/sources/{sourceId}/imports", admin.projectId(), admin.sourceId())
                        .session(member).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/projects/{projectId}/sources/{sourceId}/imports", admin.projectId(), admin.sourceId())
                        .session(member).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/projects/{projectId}/sources", admin.projectId()).session(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"owner\":\"acme\",\"repository\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/changes").param("project", admin.projectId().toString()).session(member))
                .andExpect(content().string(containsString("History import")))
                .andExpect(content().string(not(containsString("Import last 90 days"))));

        startImport(other, admin.sourceId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("source_not_found"));
        mockMvc.perform(post("/api/projects/{projectId}/sources/{sourceId}/imports", admin.projectId(), admin.sourceId())
                        .session(other.session()).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("project_not_found"));
    }

    @Test
    void theInboxStartsAnImport() throws Exception {
        Admin admin = connect("owner@example.com");
        mockMvc.perform(get("/changes").param("project", admin.projectId().toString()).session(admin.session()))
                .andExpect(content().string(containsString("Never imported")))
                .andExpect(content().string(containsString("Import last 90 days")));

        mockMvc.perform(post("/projects/{projectId}/sources/{sourceId}/imports", admin.projectId(), admin.sourceId())
                        .session(admin.session()).with(csrf()))
                .andExpect(redirectedUrl("/changes?project=" + admin.projectId() + "#source-imports"));
        mockMvc.perform(post("/projects/{projectId}/sources/{sourceId}/imports", admin.projectId(), admin.sourceId())
                        .session(admin.session()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("An import of this source is already under way.")));
        mockMvc.perform(get("/changes").param("project", admin.projectId().toString()).session(admin.session()))
                .andExpect(content().string(containsString("Queued")))
                .andExpect(content().string(not(containsString("Import last 90 days"))));
    }

    private Instant nextAttemptAt() {
        return jdbcTemplate.queryForObject("SELECT next_attempt_at FROM source_sync_jobs", Timestamp.class)
                .toInstant();
    }

    private void makeDue() {
        jdbcTemplate.update("UPDATE source_sync_jobs SET next_attempt_at = now() - interval '1 second' "
                + "WHERE status = 'RETRY_SCHEDULED'");
    }

    private ResultActions startImport(Admin admin, UUID sourceId) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/sources/{sourceId}/imports", admin.projectId(), sourceId)
                .session(admin.session()).with(csrf()));
    }

    private ResultActions resume(Admin admin, UUID sourceId) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/sources/{sourceId}/imports/resume", admin.projectId(),
                sourceId).session(admin.session()).with(csrf()));
    }

    private void deliver(Admin admin, int number) throws Exception {
        deliverExpecting(admin, number, "recorded");
    }

    private void deliverExpecting(Admin admin, int number, String outcome) throws Exception {
        String body = """
                {"action":"closed","number":%d,"pull_request":{"number":%d,"title":"feat: change %d","body":null,\
                "merged":true,"merged_at":"%s",\
                "merge_commit_sha":"%040x",\
                "html_url":"https://github.com/acme/releaseflow/pull/%d","user":{"login":"mai-dev"},\
                "base":{"ref":"main"},"labels":[]},"repository":{"full_name":"acme/releaseflow"}}"""
                .formatted(number, number, number, Instant.now().minus(Duration.ofDays(2)), number, number);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(admin.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        mockMvc.perform(post(admin.webhookPath())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.getBytes(StandardCharsets.UTF_8))
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", UUID.randomUUID().toString())
                        .header("X-Hub-Signature-256", signature))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value(outcome));
    }

    // Registers an administrator with a Project, repository acme/releaseflow, and an access token.
    private Admin connect(String email) throws Exception {
        RegistrationRequest registration = new RegistrationRequest();
        registration.setOrganizationName(email);
        registration.setDisplayName("Owner");
        registration.setEmail(email);
        registration.setPassword("owner-password");
        RegistrationResult registered = registrationService.register(registration);
        MockHttpSession session = login(email, "owner-password");
        MvcResult project = mockMvc.perform(post("/api/projects").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ReleaseFlow\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID projectId = UUID.fromString(JsonPath.read(project.getResponse().getContentAsString(), "$.id"));
        String source = mockMvc.perform(post("/api/projects/{projectId}/sources", projectId).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GITHUB\",\"owner\":\"acme\",\"repository\":\"releaseflow\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Admin admin = new Admin(registered.organizationId(), projectId, UUID.fromString(JsonPath.read(source, "$.id")),
                JsonPath.read(source, "$.webhookPath"), JsonPath.read(source, "$.webhookSecret"), session);
        setToken(admin, admin.sourceId());
        return admin;
    }

    private UUID createSource(Admin admin, String repository) throws Exception {
        String source = mockMvc.perform(post("/api/projects/{projectId}/sources", admin.projectId())
                        .session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"acme\",\"repository\":\"%s\"}".formatted(repository)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(source, "$.id"));
    }

    private void setToken(Admin admin, UUID sourceId) throws Exception {
        mockMvc.perform(put("/api/projects/{projectId}/sources/{sourceId}/token", admin.projectId(), sourceId)
                        .session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(TOKEN)))
                .andExpect(status().isNoContent());
    }

    private MockHttpSession member(UUID organizationId) throws Exception {
        jdbcTemplate.update(
                """
                        INSERT INTO app_users (id, organization_id, email, password_hash, display_name, role, created_at)
                        VALUES (?, ?, 'member@example.com', ?, 'Member', 'MEMBER', now())
                        """,
                UUID.randomUUID(), organizationId, passwordEncoder.encode("member-password")
        );
        return login("member@example.com", "member-password");
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/login").with(csrf()).param("email", email).param("password", password))
                .andExpect(status().isFound())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private record Admin(
            UUID organizationId,
            UUID projectId,
            UUID sourceId,
            String webhookPath,
            String secret,
            MockHttpSession session
    ) {
    }
}
