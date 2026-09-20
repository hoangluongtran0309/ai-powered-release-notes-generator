package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.source.ChangedFile;
import com.hoangluongtran0309.releaseflow.source.ChangedFileKind;
import com.hoangluongtran0309.releaseflow.source.ChangedFiles;
import com.hoangluongtran0309.releaseflow.support.GitHubStub;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.hoangluongtran0309.releaseflow.support.TestCategories;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChangeProcessingIntegrationTest extends PostgreSqlIntegrationTest {

    private static final GitHubStub GITHUB = GitHubStub.start();
    private static final String TOKEN = "github_pat_processing-test";
    private static final String MIGRATION = "src/main/resources/db/migration/V9__audit.sql";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private ChangeProcessingWorker worker;

    @Autowired
    private ChangeRepository changeRepository;

    @Autowired
    private ChangeProcessingJobRepository jobRepository;

    @Autowired
    private ProjectSensitivePathService sensitivePathService;

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
        jdbcTemplate.execute("TRUNCATE automation_action_runs, automation_runs, automation_publish_jobs, automation_rule_actions, automation_rules, release_audience_notes, release_change_reviews, release_notes, release_changes, releases");
        jdbcTemplate.update("DELETE FROM change_processing_jobs");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM integration_sources");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void forcesReviewOfAChangeThatTouchesAMigration() throws Exception {
        Repository repository = connect(true);
        GITHUB.respondWithFiles("src/main/java/Audit.java", MIGRATION);

        deliver(repository, 42, "feat: add audit table");
        assertThat(worker.processOne()).isTrue();
        assertThat(worker.processOne()).isFalse();

        Change change = onlyChange();
        assertThat(change.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(change.getChangedFileStatus()).isEqualTo(ChangedFileStatus.COLLECTED);
        assertThat(change.getChangedFiles()).containsExactly(
                new ChangedFile("src/main/java/Audit.java", null, ChangedFileKind.MODIFIED),
                new ChangedFile(MIGRATION, null, ChangedFileKind.MODIFIED)
        );
        assertThat(change.getCategory()).isEqualTo(TestCategories.FEATURE);
        assertThat(change.isNeedsReview()).isTrue();
        assertThat(change.getReviewTriggers()).containsExactly(ReviewTrigger.sensitivePath(MIGRATION));
        assertThat(jobRepository.findByChangeId(change.getId())).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(ChangeProcessingJob.Status.COMPLETED);
            assertThat(job.getAttempts()).isOne();
            assertThat(job.getLastError()).isNull();
        });
        assertThat(GITHUB.fileRequests()).singleElement().satisfies(request -> {
            assertThat(request.path()).isEqualTo("/repos/acme/releaseflow/pulls/42/files");
            assertThat(request.authorization()).isEqualTo("Bearer " + TOKEN);
        });

        mockMvc.perform(get("/api/projects/{projectId}/changes", repository.projectId()).session(repository.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].processingStatus").value("COMPLETED"))
                .andExpect(jsonPath("$[0].changedFileStatus").value("COLLECTED"))
                .andExpect(jsonPath("$[0].changedFiles[1].path").value(MIGRATION))
                .andExpect(jsonPath("$[0].reviewTriggers[0].type").value("SENSITIVE_PATH"))
                .andExpect(jsonPath("$[0].reviewTriggers[0].detail").value(MIGRATION));
        mockMvc.perform(get("/changes").param("project", repository.projectId().toString()).session(repository.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Review required:")))
                .andExpect(content().string(containsString("Sensitive file " + MIGRATION)))
                .andExpect(content().string(containsString("Changed files (2)")));

        // Only a recorded review settles it; the trigger stays as evidence.
        mockMvc.perform(post("/api/projects/{projectId}/changes/{changeId}/review", repository.projectId(), change.getId())
                        .session(repository.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"feature\",\"breaking\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsReview").value(false));
        assertThat(onlyChange().getReviewTriggers()).containsExactly(ReviewTrigger.sensitivePath(MIGRATION));
    }

    @Test
    void aPatternAddedForTheProjectForcesReviewOfLaterChanges() throws Exception {
        Repository repository = connect(true);
        String invoice = "src/main/java/billing/Invoice.java";
        GITHUB.respondWithFiles(invoice);
        deliver(repository, 20, "feat: add invoices");
        worker.processOne();

        mockMvc.perform(put("/api/projects/{projectId}/sensitive-paths", repository.projectId())
                        .session(repository.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"additions\":[\"**/billing/**\"]}"))
                .andExpect(status().isOk());
        deliver(repository, 21, "feat: add refunds");
        worker.processOne();

        Change earlier = change(20);
        assertThat(earlier.isNeedsReview()).isFalse();
        assertThat(earlier.getReviewTriggers()).isEmpty();
        Change later = change(21);
        assertThat(later.isNeedsReview()).isTrue();
        assertThat(later.getReviewTriggers()).containsExactly(ReviewTrigger.sensitivePath(invoice));
        mockMvc.perform(get("/changes").param("project", repository.projectId().toString()).session(repository.session()))
                .andExpect(content().string(containsString("Sensitive file " + invoice)));

        // Another Project of the Organization keeps the baseline only.
        MvcResult other = mockMvc.perform(post("/api/projects").session(repository.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Billing\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID otherProject = UUID.fromString(JsonPath.read(other.getResponse().getContentAsString(), "$.id"));
        List<ChangedFile> files = List.of(new ChangedFile(invoice, null, ChangedFileKind.MODIFIED));
        assertThat(sensitivePathService.forProject(repository.organizationId(), otherProject).matches(files)).isEmpty();
        assertThat(sensitivePathService.forProject(repository.organizationId(), repository.projectId()).matches(files))
                .containsExactly(invoice);
    }

    @Test
    void settlesAnOrdinaryChangeByTheRules() throws Exception {
        Repository repository = connect(true);
        GITHUB.respondWithFiles("src/main/java/Export.java");

        deliver(repository, 7, "feat: add CSV export");
        worker.processOne();

        Change change = onlyChange();
        assertThat(change.getCategory()).isEqualTo(TestCategories.FEATURE);
        assertThat(change.isNeedsReview()).isFalse();
        assertThat(change.getReviewTriggers()).isEmpty();
        assertThat(change.getClassificationReasons()).containsExactly("Title type \"feat\"");
        // Without an AI answer, context is not assessed.
        assertThat(change.getContext()).isNull();
    }

    @Test
    void withoutATokenEveryChangeNeedsReview() throws Exception {
        Repository repository = connect(false);

        deliver(repository, 8, "fix: trim input");
        worker.processOne();

        Change change = onlyChange();
        assertThat(change.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(change.getChangedFileStatus()).isEqualTo(ChangedFileStatus.UNAVAILABLE);
        assertThat(change.getChangedFiles()).isEmpty();
        assertThat(change.getCategory()).isEqualTo(TestCategories.FIX);
        assertThat(change.isNeedsReview()).isTrue();
        assertThat(change.getReviewTriggers()).containsExactly(ReviewTrigger.changedFilesUnavailable());
        assertThat(jobRepository.findByChangeId(change.getId()))
                .hasValueSatisfying(job -> assertThat(job.getLastError()).isEqualTo(ChangedFiles.NO_ACCESS_TOKEN));
        assertThat(GITHUB.requests()).isEmpty();

        mockMvc.perform(get("/changes").param("project", repository.projectId().toString()).session(repository.session()))
                .andExpect(content().string(containsString("Changed files unavailable")));
    }

    @Test
    void retriesTemporaryFailuresWithBackoffThenForcesReview() throws Exception {
        Repository repository = connect(true);
        GITHUB.failFiles(503);
        deliver(repository, 9, "chore: bump dependencies");
        UUID changeId = onlyChange().getId();

        Instant beforeFirstAttempt = Instant.now();
        assertThat(worker.processOne()).isTrue();
        ChangeProcessingJob afterFirst = jobRepository.findByChangeId(changeId).orElseThrow();
        assertThat(afterFirst.getStatus()).isEqualTo(ChangeProcessingJob.Status.PENDING);
        assertThat(afterFirst.getAttempts()).isOne();
        assertThat(afterFirst.getLastError()).isEqualTo(ChangedFiles.GITHUB_UNAVAILABLE);
        assertThat(afterFirst.getNextAttemptAt()).isAfterOrEqualTo(beforeFirstAttempt.plusSeconds(1));
        assertThat(onlyChange().getProcessingStatus()).isEqualTo(ProcessingStatus.PROCESSING);

        // Not due yet.
        assertThat(worker.processOne()).isFalse();

        makeDue(changeId);
        worker.processOne();
        assertThat(jobRepository.findByChangeId(changeId).orElseThrow().getAttempts()).isEqualTo(2);
        assertThat(onlyChange().getProcessingStatus()).isEqualTo(ProcessingStatus.PROCESSING);

        makeDue(changeId);
        worker.processOne();

        Change change = onlyChange();
        assertThat(change.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(change.getCategory()).isEqualTo(TestCategories.MAINTENANCE);
        assertThat(change.isNeedsReview()).isTrue();
        assertThat(change.getReviewTriggers()).containsExactly(ReviewTrigger.changedFilesUnavailable());
        assertThat(jobRepository.findByChangeId(changeId)).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(ChangeProcessingJob.Status.COMPLETED);
            assertThat(job.getAttempts()).isEqualTo(ChangeProcessingWorker.MAX_ATTEMPTS);
        });
        assertThat(GITHUB.fileRequests()).hasSize(ChangeProcessingWorker.MAX_ATTEMPTS);
    }

    @Test
    void aRefusedTokenIsNotRetried() throws Exception {
        Repository repository = connect(true);
        GITHUB.failFiles(404);

        deliver(repository, 10, "feat: add export");
        worker.processOne();

        Change change = onlyChange();
        assertThat(change.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(change.getReviewTriggers()).containsExactly(ReviewTrigger.changedFilesUnavailable());
        assertThat(GITHUB.fileRequests()).hasSize(1);
    }

    @Test
    void concurrentWorkersClaimEachJobOnce() throws Exception {
        Repository repository = connect(true);
        GITHUB.respondWithFiles("src/main/java/Export.java");
        GITHUB.delay(Duration.ofMillis(300));
        for (int number = 1; number <= 3; number++) {
            deliver(repository, number, "feat: change " + number);
        }

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> workers = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                workers.add(() -> {
                    boolean processed = false;
                    while (worker.processOne()) {
                        processed = true;
                    }
                    return processed;
                });
            }
            for (Future<Boolean> result : executor.invokeAll(workers)) {
                result.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(changeRepository.findAll())
                .hasSize(3)
                .allSatisfy(change -> assertThat(change.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED));
        assertThat(GITHUB.fileRequests())
                .extracting(GitHubStub.RecordedRequest::path)
                .containsExactlyInAnyOrder(
                        "/repos/acme/releaseflow/pulls/1/files",
                        "/repos/acme/releaseflow/pulls/2/files",
                        "/repos/acme/releaseflow/pulls/3/files"
                );
        assertThat(jobRepository.findAll()).allSatisfy(job -> assertThat(job.getAttempts()).isOne());
    }

    @Test
    void recoversAClaimLeftByAStoppedWorker() throws Exception {
        Repository repository = connect(true);
        GITHUB.respondWithFiles("src/main/java/Export.java");
        deliver(repository, 11, "feat: add export");
        UUID changeId = onlyChange().getId();
        jdbcTemplate.update(
                "UPDATE change_processing_jobs SET status = 'ENRICHING', attempts = 1, claimed_at = ? WHERE change_id = ?",
                Timestamp.from(Instant.now().minus(ChangeProcessingWorker.STALE_AFTER).minusSeconds(60)),
                changeId
        );

        assertThat(worker.processOne()).isTrue();

        assertThat(onlyChange().getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(jobRepository.findByChangeId(changeId).orElseThrow().getAttempts()).isEqualTo(2);
    }

    @Test
    void aRecentClaimIsLeftToItsWorker() throws Exception {
        Repository repository = connect(true);
        deliver(repository, 12, "feat: add export");
        UUID changeId = onlyChange().getId();
        jdbcTemplate.update(
                "UPDATE change_processing_jobs SET status = 'ENRICHING', attempts = 1, claimed_at = ? WHERE change_id = ?",
                Timestamp.from(Instant.now().minusSeconds(30)),
                changeId
        );

        assertThat(worker.processOne()).isFalse();
        assertThat(onlyChange().getProcessingStatus()).isEqualTo(ProcessingStatus.PROCESSING);
    }

    @Test
    void aProcessingChangeCannotBeReviewedReleasedOrSuggested() throws Exception {
        Repository repository = connect(true);
        deliver(repository, 13, "feat: add export");
        UUID changeId = onlyChange().getId();

        mockMvc.perform(post("/api/projects/{projectId}/changes/{changeId}/review", repository.projectId(), changeId)
                        .session(repository.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"feature\",\"breaking\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("change_processing"));
        mockMvc.perform(post("/api/projects/{projectId}/changes/{changeId}/ai-classification", repository.projectId(), changeId)
                        .session(repository.session())
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("change_processing"));

        MvcResult release = mockMvc.perform(post("/api/projects/{projectId}/releases", repository.projectId())
                        .session(repository.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.0.0\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String releaseId = JsonPath.read(release.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}/available-changes",
                        repository.projectId(), releaseId).session(repository.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/changes", repository.projectId(), releaseId)
                        .session(repository.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changeIds\":[\"%s\"]}".formatted(changeId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("change_not_releasable"));

        mockMvc.perform(get("/changes").param("project", repository.projectId().toString()).session(repository.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Processing")))
                .andExpect(content().string(containsString("Checking which files this change touched")));
    }

    private void makeDue(UUID changeId) {
        jdbcTemplate.update(
                "UPDATE change_processing_jobs SET next_attempt_at = ? WHERE change_id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)),
                changeId
        );
    }

    private Change change(int pullRequestNumber) {
        return changeRepository.findAll().stream()
                .filter(change -> change.getPullRequestNumber() == pullRequestNumber)
                .findFirst()
                .orElseThrow();
    }

    private Change onlyChange() {
        List<Change> changes = changeRepository.findAll();
        assertThat(changes).hasSize(1);
        return changes.getFirst();
    }

    private void deliver(Repository repository, int number, String title) throws Exception {
        String body = """
                {"action":"closed","number":%d,"pull_request":{"number":%d,"title":"%s","body":null,\
                "merged":true,"merged_at":"2026-09-10T09:14:22Z",\
                "merge_commit_sha":"0123456789abcdef0123456789abcdef01234567",\
                "html_url":"https://github.com/acme/releaseflow/pull/%d","user":{"login":"mai-dev"},\
                "base":{"ref":"main"},"labels":[]},"repository":{"full_name":"acme/releaseflow"}}"""
                .formatted(number, number, title, number);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(repository.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        mockMvc.perform(post(repository.webhookPath())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.getBytes(StandardCharsets.UTF_8))
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", UUID.randomUUID().toString())
                        .header("X-Hub-Signature-256", signature))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("recorded"));
    }

    private Repository connect(boolean withToken) throws Exception {
        RegistrationRequest registration = new RegistrationRequest();
        registration.setOrganizationName("Acme");
        registration.setDisplayName("Owner");
        registration.setEmail("owner@example.com");
        registration.setPassword("owner-password");
        RegistrationResult registered = registrationService.register(registration);
        MvcResult login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", "owner@example.com")
                        .param("password", "owner-password"))
                .andExpect(status().isFound())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        MvcResult project = mockMvc.perform(post("/api/projects")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ReleaseFlow\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID projectId = UUID.fromString(JsonPath.read(project.getResponse().getContentAsString(), "$.id"));
        MvcResult integration = mockMvc.perform(post("/api/projects/{projectId}/sources", projectId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"acme\",\"repository\":\"releaseflow\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        if (withToken) {
            mockMvc.perform(put("/api/projects/{projectId}/sources/{sourceId}/token", projectId,
                            JsonPath.read(integration.getResponse().getContentAsString(), "$.id"))
                            .session(session)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"%s\"}".formatted(TOKEN)))
                    .andExpect(status().isNoContent());
            GITHUB.reset();
        }
        String body = integration.getResponse().getContentAsString();
        return new Repository(
                registered.organizationId(),
                projectId,
                JsonPath.read(body, "$.webhookPath"),
                JsonPath.read(body, "$.webhookSecret"),
                session
        );
    }

    private record Repository(
            UUID organizationId,
            UUID projectId,
            String webhookPath,
            String secret,
            MockHttpSession session
    ) {
    }
}
