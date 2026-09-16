package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.source.ChangedFiles;
import com.hoangluongtran0309.releaseflow.support.GitLabStub;
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
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.hoangluongtran0309.releaseflow.support.GitLabStub.mergedMergeRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Importing a GitLab project's merged merge requests from a stand-in for its REST API. */
class GitLabImportIntegrationTest extends PostgreSqlIntegrationTest {

    private static final GitLabStub GITLAB = GitLabStub.start();
    private static final String TOKEN = "glpat-import-test";
    private static final String PROJECT_PATH = "acme/group/app";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private SourceImportWorker importWorker;

    @Autowired
    private ChangeProcessingWorker changeWorker;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void gitLabProperties(DynamicPropertyRegistry registry) {
        registry.add("releaseflow.gitlab.allowed-hosts", () -> "gitlab.com," + GITLAB.allowedOrigin());
        registry.add("releaseflow.gitlab.timeout", () -> "PT2S");
    }

    @AfterAll
    static void stopStub() {
        GITLAB.close();
    }

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        GITLAB.reset();
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
    void importsTheMergedMergeRequestsOfTheLastNinetyDaysAcrossPages() throws Exception {
        Admin admin = connect("owner@example.com");
        Instant now = Instant.now();
        List<Map<String, Object>> merged = new ArrayList<>();
        for (int iid = 1; iid <= 120; iid++) {
            Instant mergedAt = now.minus(Duration.ofDays(1)).minus(Duration.ofMinutes(iid));
            merged.add(mergedMergeRequest(iid, mergedAt, mergedAt.plusSeconds(60)));
        }
        // Merged before the window, and updated inside it, so neither belongs to this import.
        merged.add(mergedMergeRequest(121, now.minus(Duration.ofDays(100)), now.minus(Duration.ofDays(1))));
        GITLAB.respondWithMergeRequests(merged);

        startImport(admin).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(importWorker.processOne()).isTrue();
        assertThat(importWorker.processOne()).isFalse();

        assertThat(GITLAB.listRequests()).hasSize(2).allSatisfy(request -> {
            assertThat(request.path()).isEqualTo("/api/v4/projects/acme%2Fgroup%2Fapp/merge_requests");
            assertThat(request.query()).contains("state=merged", "order_by=updated_at", "sort=asc", "per_page=100",
                    "updated_after=");
            assertThat(request.privateToken()).isEqualTo(TOKEN);
        });
        assertThat(GITLAB.listRequests()).extracting(GitLabStub.RecordedRequest::query)
                .anySatisfy(query -> assertThat(query).contains("&page=1"))
                .anySatisfy(query -> assertThat(query).contains("&page=2"));
        mockMvc.perform(get("/api/projects/{projectId}/imports", admin.projectId()).session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].externalProjectKey").value(PROJECT_PATH))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].scannedCount").value(121))
                .andExpect(jsonPath("$[0].importedCount").value(120));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM changes WHERE origin = 'IMPORT' AND delivery_id IS NULL", Long.class))
                .isEqualTo(120);
        assertThat(jdbcTemplate.queryForList("SELECT pull_request_number FROM changes", Integer.class))
                .doesNotContain(121);

        // Imported changes go through the same processing as webhook deliveries.
        GITLAB.respondWithFiles("src/main/java/Export.java");
        assertThat(changeWorker.processOne()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM changes WHERE processing_status = 'COMPLETED'", Long.class)).isOne();
        assertThat(GITLAB.diffRequests()).hasSize(1);
        assertThat(GITLAB.diffRequests().getFirst().path())
                .startsWith("/api/v4/projects/acme%2Fgroup%2Fapp/merge_requests/");
    }

    @Test
    void stopsAtItsLimitAndResumesWhereItStopped() throws Exception {
        Admin admin = connect("owner@example.com");
        Instant now = Instant.now();
        List<Map<String, Object>> merged = new ArrayList<>();
        for (int iid = 1; iid <= 5; iid++) {
            Instant mergedAt = now.minus(Duration.ofDays(iid));
            merged.add(mergedMergeRequest(iid, mergedAt, mergedAt));
        }
        GITLAB.respondWithMergeRequests(merged);
        startImport(admin).andExpect(status().isAccepted());
        jdbcTemplate.update("UPDATE source_sync_jobs SET item_limit = 2");

        importWorker.processOne();
        assertThat(jdbcTemplate.queryForMap("SELECT status, provider_cursor, imported_count FROM source_sync_jobs"))
                .containsEntry("status", "PARTIAL")
                .containsEntry("provider_cursor", "1:2")
                .containsEntry("imported_count", 2);

        resume(admin).andExpect(status().isAccepted()).andExpect(jsonPath("$.importedCount").value(0));
        importWorker.processOne();
        assertThat(jdbcTemplate.queryForMap("SELECT status, provider_cursor FROM source_sync_jobs"))
                .containsEntry("status", "PARTIAL")
                .containsEntry("provider_cursor", "1:4");

        resume(admin).andExpect(status().isAccepted());
        importWorker.processOne();
        assertThat(jdbcTemplate.queryForMap("SELECT status, imported_count, scanned_count FROM source_sync_jobs"))
                .containsEntry("status", "COMPLETED")
                .containsEntry("imported_count", 1)
                .containsEntry("scanned_count", 5);
        assertThat(jdbcTemplate.queryForList("SELECT pull_request_number FROM changes ORDER BY pull_request_number",
                Integer.class)).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void waitsAsLongAsGitLabAsksAndGivesUpAfterFiveAttempts() throws Exception {
        Admin admin = connect("owner@example.com");
        GITLAB.respondWithMergeRequests(List.of(mergedMergeRequest(1, Instant.now(), Instant.now())));
        startImport(admin).andExpect(status().isAccepted());

        GITLAB.failMergeRequestList(429, Map.of("Retry-After", "120"), 1);
        Instant before = Instant.now();
        importWorker.processOne();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM source_sync_jobs", String.class))
                .isEqualTo("RETRY_SCHEDULED");
        assertThat(nextAttemptAt()).isCloseTo(before.plusSeconds(120), within(Duration.ofSeconds(10)));
        assertThat(importWorker.processOne()).as("not due yet").isFalse();

        makeDue();
        GITLAB.failMergeRequestList(429, Map.of("Retry-After", "7200"), 1);
        importWorker.processOne();
        assertThat(nextAttemptAt()).as("capped at an hour")
                .isCloseTo(Instant.now().plus(Duration.ofHours(1)), within(Duration.ofSeconds(10)));

        GITLAB.failMergeRequestList(502, Map.of(), 3);
        for (int attempt = 0; attempt < 3; attempt++) {
            makeDue();
            importWorker.processOne();
        }
        assertThat(jdbcTemplate.queryForMap("SELECT status, attempts, last_error FROM source_sync_jobs"))
                .containsEntry("status", "FAILED")
                .containsEntry("attempts", SourceImportWorker.MAX_ATTEMPTS)
                .containsEntry("last_error", ChangedFiles.GITLAB_UNAVAILABLE);
        mockMvc.perform(get("/api/projects/{projectId}/imports", admin.projectId()).session(admin.session()))
                .andExpect(jsonPath("$[0].lastErrorCode").value("gitlab_unavailable"))
                .andExpect(jsonPath("$[0].canResumeImport").value(true));
    }

    @Test
    void aRefusedTokenFailsTheImportAndMarksTheSource() throws Exception {
        Admin admin = connect("owner@example.com");
        startImport(admin).andExpect(status().isAccepted());
        GITLAB.failMergeRequestList(401, Map.of(), 1);

        importWorker.processOne();

        assertThat(jdbcTemplate.queryForMap("SELECT status, attempts, last_error FROM source_sync_jobs"))
                .containsEntry("status", "FAILED")
                .containsEntry("attempts", 1)
                .containsEntry("last_error", SourceImportWorker.ACCESS_REJECTED);
        mockMvc.perform(get("/api/projects/{projectId}/sources", admin.projectId()).session(admin.session()))
                .andExpect(jsonPath("$[0].connectionStatus").value("ERROR"))
                .andExpect(jsonPath("$[0].lastErrorCode").value("access_rejected"));
    }

    @Test
    void anImportNeverDuplicatesAWebhookDelivery() throws Exception {
        Admin admin = connect("owner@example.com");
        Instant now = Instant.now();
        deliver(admin, 7, "recorded");
        GITLAB.respondWithMergeRequests(List.of(
                mergedMergeRequest(7, now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(2))),
                mergedMergeRequest(8, now.minus(Duration.ofDays(3)), now.minus(Duration.ofDays(3)))
        ));

        startImport(admin).andExpect(status().isAccepted());
        importWorker.processOne();

        assertThat(jdbcTemplate.queryForMap("SELECT scanned_count, imported_count FROM source_sync_jobs"))
                .containsEntry("scanned_count", 2)
                .containsEntry("imported_count", 1);
        assertThat(jdbcTemplate.queryForList("SELECT origin FROM changes ORDER BY pull_request_number", String.class))
                .containsExactly("WEBHOOK", "IMPORT");
        deliver(admin, 8, "duplicate");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM changes", Long.class)).isEqualTo(2);
    }

    @Test
    void anUnreadableMergeRequestDoesNotStopTheRest() throws Exception {
        Admin admin = connect("owner@example.com");
        Instant now = Instant.now();
        Map<String, Object> broken = new java.util.LinkedHashMap<>(mergedMergeRequest(2, now, now));
        broken.put("title", "  ");
        GITLAB.respondWithMergeRequests(List.of(mergedMergeRequest(1, now, now), broken,
                mergedMergeRequest(3, now, now)));

        startImport(admin).andExpect(status().isAccepted());
        importWorker.processOne();

        assertThat(jdbcTemplate.queryForMap("SELECT status, scanned_count, imported_count FROM source_sync_jobs"))
                .containsEntry("status", "COMPLETED")
                .containsEntry("scanned_count", 3)
                .containsEntry("imported_count", 2);
        assertThat(jdbcTemplate.queryForList("SELECT pull_request_number FROM changes ORDER BY pull_request_number",
                Integer.class)).containsExactly(1, 3);
    }

    private Instant nextAttemptAt() {
        return jdbcTemplate.queryForObject("SELECT next_attempt_at FROM source_sync_jobs", Timestamp.class).toInstant();
    }

    private void makeDue() {
        jdbcTemplate.update("UPDATE source_sync_jobs SET next_attempt_at = now() - interval '1 second' "
                + "WHERE status = 'RETRY_SCHEDULED'");
    }

    private ResultActions startImport(Admin admin) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/sources/{sourceId}/imports", admin.projectId(),
                admin.sourceId()).session(admin.session()).with(csrf()));
    }

    private ResultActions resume(Admin admin) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/sources/{sourceId}/imports/resume", admin.projectId(),
                admin.sourceId()).session(admin.session()).with(csrf()));
    }

    private void deliver(Admin admin, int iid, String outcome) throws Exception {
        String body = """
                {"object_kind":"merge_request","user":{"username":"mai-dev"},\
                "project":{"path_with_namespace":"%s"},"labels":[],\
                "object_attributes":{"iid":%d,"action":"merge","title":"feat: change %d","description":null,\
                "target_branch":"main","merge_commit_sha":"%040x","merged_at":"%s",\
                "url":"https://gitlab.com/%s/-/merge_requests/%d"}}"""
                .formatted(PROJECT_PATH, iid, iid, iid, Instant.now().minus(Duration.ofDays(2)), PROJECT_PATH, iid);
        String deliveryId = UUID.randomUUID().toString();
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getUrlDecoder().decode(admin.secret()), "HmacSHA256"));
        String signature = "v1," + Base64.getEncoder().encodeToString(
                mac.doFinal((deliveryId + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        mockMvc.perform(post(admin.webhookPath())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.getBytes(StandardCharsets.UTF_8))
                        .header("webhook-id", deliveryId)
                        .header("webhook-timestamp", timestamp)
                        .header("webhook-signature", signature))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value(outcome));
    }

    // Registers an administrator with a Project, a GitLab source on the stub, and a token.
    private Admin connect(String email) throws Exception {
        RegistrationRequest registration = new RegistrationRequest();
        registration.setOrganizationName(email);
        registration.setDisplayName("Owner");
        registration.setEmail(email);
        registration.setPassword("owner-password");
        RegistrationResult registered = registrationService.register(registration);
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
        String source = mockMvc.perform(post("/api/projects/{projectId}/sources", projectId).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"GITLAB","apiBaseUrl":"%s","projectPath":"%s",\
                                "webhookAuthMode":"GITLAB_SIGNING_TOKEN"}"""
                                .formatted(GITLAB.baseUrl(), PROJECT_PATH)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Admin admin = new Admin(registered.organizationId(), projectId, UUID.fromString(JsonPath.read(source, "$.id")),
                JsonPath.read(source, "$.webhookPath"), JsonPath.read(source, "$.webhookSecret"), session);
        mockMvc.perform(put("/api/projects/{projectId}/sources/{sourceId}/token", projectId, admin.sourceId())
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(TOKEN)))
                .andExpect(status().isNoContent());
        GITLAB.reset();
        return admin;
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
