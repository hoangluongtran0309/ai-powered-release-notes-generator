package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.GitHubStub;
import com.hoangluongtran0309.releaseflow.support.OpenAiStub;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChangeAiProcessingIntegrationTest extends PostgreSqlIntegrationTest {

    private static final GitHubStub GITHUB = GitHubStub.start();
    private static final OpenAiStub OPENAI = OpenAiStub.start();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
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
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        registry.add("releaseflow.github.api-base-url", GITHUB::baseUrl);
        registry.add("releaseflow.github.timeout", () -> "PT2S");
        registry.add("releaseflow.ai.provider", () -> "openai");
        registry.add("releaseflow.ai.timeout", () -> "PT2S");
        registry.add("releaseflow.openai.base-url", OPENAI::baseUrl);
        registry.add("releaseflow.openai.api-key", () -> "sk-processing-test");
        registry.add("releaseflow.openai.model", () -> "gpt-test");
    }

    @AfterAll
    static void stopStubs() {
        GITHUB.close();
        OPENAI.close();
    }

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        GITHUB.reset();
        OPENAI.reset();
        jdbcTemplate.execute("TRUNCATE release_audience_notes, release_change_reviews, release_notes, release_changes, releases");
        jdbcTemplate.update("DELETE FROM change_processing_jobs");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM github_integrations");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteAudiences();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void summarizesAndClassifiesEveryNewChangeInTheOrganizationsLanguage() throws Exception {
        Repository repository = connect();
        mockMvc.perform(put("/api/organization/output-language")
                        .session(repository.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outputLanguage\":\"vi\"}"))
                .andExpect(status().isOk());
        GITHUB.respondWithFiles("src/main/java/Export.java");
        OPENAI.respondWithClassification("fix", false, false, "Sửa lỗi xuất bảng rỗng.");

        deliver(repository, 7, "Tidy the exporter");
        assertThat(worker.processOne()).isTrue();

        Change change = onlyChange();
        assertThat(change.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(change.getCategory()).isEqualTo(ChangeCategory.FIX);
        assertThat(change.isNeedsReview()).isFalse();
        assertThat(change.getClassificationSource()).isEqualTo(ClassificationSource.AI);
        assertThat(change.getAiStatus()).isEqualTo(AiStatus.SUCCEEDED);
        assertThat(change.getAiProvider()).isEqualTo(AiProvider.OPENAI);
        assertThat(change.getAiModel()).isEqualTo("gpt-test");
        assertThat(change.getNeutralSummary().whatChanged()).isEqualTo("Sửa lỗi xuất bảng rỗng.");
        assertThat(change.getContentLanguage()).isEqualTo("vi");
        assertThat(change.getReviewTriggers()).isEmpty();
        assertThat(jobRepository.findByChangeId(change.getId()).orElseThrow().getStatus())
                .isEqualTo(ChangeProcessingJob.Status.COMPLETED);

        assertThat(OPENAI.requests()).hasSize(1);
        JsonNode user = userMessage(OPENAI.requests().getFirst());
        assertThat(user.path("output_language").stringValue()).isEqualTo("Vietnamese (vi)");
        assertThat(user.path("locked_category").isNull()).isTrue();

        mockMvc.perform(get("/api/projects/{projectId}/changes", repository.projectId()).session(repository.session()))
                .andExpect(jsonPath("$[0].neutralSummary.whatChanged").value("Sửa lỗi xuất bảng rỗng."))
                .andExpect(jsonPath("$[0].contentLanguage").value("vi"))
                .andExpect(jsonPath("$[0].aiProvider").value("OPENAI"));
        mockMvc.perform(get("/changes").param("project", repository.projectId().toString()).session(repository.session()))
                .andExpect(content().string(containsString("Summary (vi)")))
                .andExpect(content().string(containsString("Sửa lỗi xuất bảng rỗng.")))
                .andExpect(content().string(containsString("AI category")));
    }

    @Test
    void theRulesLockTheCategoryAndSensitiveFilesStillForceReview() throws Exception {
        Repository repository = connect();
        GITHUB.respondWithFiles("src/main/java/Audit.java", MIGRATION);
        OPENAI.respondWithClassification("maintenance", false, false, "Adds an audit table.");

        deliver(repository, 8, "feat: add audit table");
        worker.processOne();

        Change change = onlyChange();
        assertThat(change.getCategory()).isEqualTo(ChangeCategory.FEATURE);
        assertThat(change.getClassificationSource()).isEqualTo(ClassificationSource.RULES);
        assertThat(change.getAiStatus()).isEqualTo(AiStatus.SUCCEEDED);
        assertThat(change.getNeutralSummary().whatChanged()).isEqualTo("Adds an audit table.");
        assertThat(change.isNeedsReview()).isTrue();
        assertThat(change.getReviewTriggers()).containsExactly(ReviewTrigger.sensitivePath(MIGRATION));
        assertThat(userMessage(OPENAI.requests().getFirst()).path("locked_category").stringValue()).isEqualTo("feature");
    }

    @Test
    void theAiCanOnlyAddCaution() throws Exception {
        Repository repository = connect();
        GITHUB.respondWithFiles("src/main/java/Export.java");

        OPENAI.respondWithClassification("fix", false, true, "Changes rounding.");
        deliver(repository, 9, "Adjust totals");
        worker.processOne();
        OPENAI.respondWithClassification("feature", true, false, "Removes the v1 API.");
        deliver(repository, 10, "feat: new export API");
        worker.processOne();

        Change askedForReview = change(9);
        assertThat(askedForReview.getCategory()).isEqualTo(ChangeCategory.FIX);
        assertThat(askedForReview.isNeedsReview()).isTrue();
        assertThat(askedForReview.getClassificationReasons()).contains("AI asked for human review");
        Change breaking = change(10);
        assertThat(breaking.isBreaking()).isTrue();
        assertThat(breaking.isNeedsReview()).isTrue();
    }

    @Test
    void aFailedAiCallBecomesAFallbackThatAPersonCanRetry() throws Exception {
        Repository repository = connect();
        GITHUB.respondWithFiles("src/main/java/Export.java");
        OPENAI.respond(500, "{\"error\":{\"message\":\"upstream exploded\"}}");

        deliver(repository, 11, "feat: add CSV export");
        assertThat(worker.processOne()).isTrue();
        assertThat(worker.processOne()).isFalse();

        Change change = onlyChange();
        assertThat(change.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(change.getCategory()).isEqualTo(ChangeCategory.FEATURE);
        assertThat(change.isNeedsReview()).isTrue();
        assertThat(change.getAiStatus()).isEqualTo(AiStatus.FAILED);
        assertThat(change.getAiFailure()).isEqualTo("OpenAI returned HTTP 500.");
        assertThat(change.getNeutralSummary()).isNull();
        assertThat(change.getReviewTriggers()).containsExactly(ReviewTrigger.classifierFallback());
        assertThat(OPENAI.requests()).hasSize(1);
        mockMvc.perform(get("/changes").param("project", repository.projectId().toString()).session(repository.session()))
                .andExpect(content().string(containsString("AI classification failed")))
                .andExpect(content().string(containsString("Retry with AI")));

        OPENAI.respondWithClassification("feature", false, false, "Adds CSV export.");
        retry(repository, change.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.neutralSummary.whatChanged").value("Adds CSV export."))
                .andExpect(jsonPath("$.needsReview").value(true))
                .andExpect(jsonPath("$.reviewTriggers[0].type").value("CLASSIFIER_FALLBACK"))
                .andExpect(jsonPath("$.aiEligible").value(false));
        retry(repository, change.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("change_not_eligible_for_ai"));
        assertThat(OPENAI.requests()).hasSize(2);
    }

    @Test
    void aClassificationThatStalledIsFinishedWithoutAskingTheAiAgain() throws Exception {
        Repository repository = connect();
        deliver(repository, 12, "feat: add CSV export");
        UUID changeId = onlyChange().getId();
        // A worker stopped after recording the files and before the AI answered.
        jdbcTemplate.update(
                "UPDATE changes SET changed_file_status = 'COLLECTED', changed_files = CAST(? AS jsonb) WHERE id = ?",
                "[{\"path\":\"" + MIGRATION + "\",\"previousPath\":null,\"kind\":\"ADDED\"}]",
                changeId
        );
        jdbcTemplate.update(
                "UPDATE change_processing_jobs SET status = 'CLASSIFYING', attempts = 1, claimed_at = ? WHERE change_id = ?",
                Timestamp.from(Instant.now().minus(ChangeProcessingWorker.STALE_AFTER).minusSeconds(60)),
                changeId
        );

        assertThat(worker.processOne()).isTrue();

        Change change = onlyChange();
        assertThat(change.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(change.getCategory()).isEqualTo(ChangeCategory.FEATURE);
        assertThat(change.getAiStatus()).isEqualTo(AiStatus.FAILED);
        assertThat(change.getAiFailure()).isEqualTo(AiOutcome.DID_NOT_FINISH);
        assertThat(change.getReviewTriggers())
                .containsExactly(ReviewTrigger.sensitivePath(MIGRATION), ReviewTrigger.classifierFallback());
        assertThat(jobRepository.findByChangeId(changeId)).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(ChangeProcessingJob.Status.COMPLETED);
            assertThat(job.getLastError()).isEqualTo(ChangeProcessingWorker.AI_DID_NOT_FINISH);
        });
        assertThat(OPENAI.requests()).isEmpty();
        assertThat(GITHUB.fileRequests()).isEmpty();
    }

    @Test
    void aRecentClassificationIsLeftToItsWorker() throws Exception {
        Repository repository = connect();
        deliver(repository, 13, "feat: add CSV export");
        jdbcTemplate.update(
                "UPDATE change_processing_jobs SET status = 'CLASSIFYING', attempts = 1, claimed_at = ?",
                Timestamp.from(Instant.now().minusSeconds(30))
        );

        assertThat(worker.processOne()).isFalse();
        assertThat(OPENAI.requests()).isEmpty();
    }

    @Test
    void aRedeliveredPullRequestIsNotSentAgain() throws Exception {
        Repository repository = connect();
        GITHUB.respondWithFiles("src/main/java/Export.java");

        deliver(repository, 14, "feat: add CSV export");
        worker.processOne();
        String outcome = deliverRaw(repository, 14, "feat: add CSV export");
        worker.processOne();

        assertThat(outcome).contains("duplicate");
        assertThat(OPENAI.requests()).hasSize(1);
        assertThat(changeRepository.count()).isOne();
    }

    @Test
    void writesANarrativeForEveryAudienceIncludingOnesAddedLater() throws Exception {
        Repository repository = connect();
        mockMvc.perform(post("/api/audiences")
                        .session(repository.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"leadership","displayName":"Leadership","communicationIntent":"Business impact only.",
                                 "templateBody":"{{whatChanged}}"}
                                """))
                .andExpect(status().isCreated());
        GITHUB.respondWithFiles("src/main/java/Export.java");
        OPENAI.respondWithNarratives("feature", "Adds CSV export.", Map.of(
                "leadership", "Customers asked for exports most.",
                "operator", "Watch the export queue.",
                "stranger", "Not an audience."
        ));

        deliver(repository, 7, "feat: add CSV export");
        assertThat(worker.processOne()).isTrue();

        OpenAiStub.RecordedRequest request = OPENAI.requests().getFirst();
        JsonNode user = userMessage(request);
        assertThat(List.of(0, 1, 2, 3).stream().map(index -> user.path("audiences").path(index).path("code").stringValue()))
                .containsExactly("contributor", "end_user", "leadership", "operator");
        assertThat(user.path("audiences").path(2).path("intent").stringValue()).isEqualTo("Business impact only.");
        assertThat(OBJECT_MAPPER.readTree(request.body())
                .path("response_format").path("json_schema").path("schema").path("properties").path("narratives")
                .path("required").toString())
                .isEqualTo("[\"contributor\",\"end_user\",\"leadership\",\"operator\"]");

        Change change = onlyChange();
        assertThat(change.getAudienceNarratives()).containsOnly(
                Map.entry("leadership", "Customers asked for exports most."),
                Map.entry("operator", "Watch the export queue.")
        );
        mockMvc.perform(get("/changes").param("project", repository.projectId().toString()).session(repository.session()))
                .andExpect(content().string(containsString("For leadership")))
                .andExpect(content().string(containsString("Customers asked for exports most.")));
    }

    @Test
    void anAiRetryNeverReplacesASummaryAPersonWrote() throws Exception {
        Repository repository = connect();
        GITHUB.respondWithFiles("src/main/java/Export.java");
        OPENAI.respond(500, "{\"error\":{\"message\":\"upstream exploded\"}}");
        deliver(repository, 11, "feat: add CSV export");
        assertThat(worker.processOne()).isTrue();
        UUID changeId = onlyChange().getId();

        String releases = "/api/projects/" + repository.projectId() + "/releases";
        String release = mockMvc.perform(post(releases).session(repository.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"1.0.0\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String releasePath = releases + "/" + JsonPath.read(release, "$.id");
        mockMvc.perform(post(releasePath + "/changes").session(repository.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allAvailable\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(releasePath + "/request-review").session(repository.session()).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(put(releasePath + "/changes/" + changeId + "/summary").session(repository.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"whatChanged\":\"Written by a person.\",\"narratives\":{\"operator\":\"By hand.\"}}"))
                .andExpect(status().isOk());

        OPENAI.respondWithNarratives("feature", "Written by the AI.", Map.of("operator", "By the AI."));
        retry(repository, changeId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.neutralSummary.whatChanged").value("Written by a person."))
                .andExpect(jsonPath("$.audienceNarratives.operator").value("By hand."))
                .andExpect(jsonPath("$.summaryEditorName").value("Owner"));
    }

    private ResultActions retry(Repository repository, UUID changeId) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/changes/{changeId}/ai-classification",
                        repository.projectId(), changeId)
                .session(repository.session())
                .with(csrf()));
    }

    private static JsonNode userMessage(OpenAiStub.RecordedRequest request) {
        JsonNode body = OBJECT_MAPPER.readTree(request.body());
        return OBJECT_MAPPER.readTree(body.path("messages").path(1).path("content").stringValue());
    }

    private Change onlyChange() {
        List<Change> changes = changeRepository.findAll();
        assertThat(changes).hasSize(1);
        return changes.getFirst();
    }

    private Change change(int number) {
        return changeRepository.findAll().stream()
                .filter(change -> change.getPullRequestNumber() == number)
                .findFirst()
                .orElseThrow();
    }

    private void deliver(Repository repository, int number, String title) throws Exception {
        assertThat(deliverRaw(repository, number, title)).contains("recorded");
    }

    private String deliverRaw(Repository repository, int number, String title) throws Exception {
        String body = """
                {"action":"closed","number":%d,"pull_request":{"number":%d,"title":"%s","body":"Details.",\
                "merged":true,"merged_at":"2026-09-10T09:14:22Z",\
                "merge_commit_sha":"0123456789abcdef0123456789abcdef01234567",\
                "html_url":"https://github.com/acme/releaseflow/pull/%d","user":{"login":"mai-dev"},\
                "base":{"ref":"main"},"labels":[]},"repository":{"full_name":"acme/releaseflow"}}"""
                .formatted(number, number, title, number);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(repository.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        return mockMvc.perform(post(repository.webhookPath())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.getBytes(StandardCharsets.UTF_8))
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", UUID.randomUUID().toString())
                        .header("X-Hub-Signature-256", signature))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private Repository connect() throws Exception {
        RegistrationRequest registration = new RegistrationRequest();
        registration.setOrganizationName("Acme");
        registration.setDisplayName("Owner");
        registration.setEmail("owner@example.com");
        registration.setPassword("owner-password");
        registrationService.register(registration);
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
        MvcResult integration = mockMvc.perform(post("/api/projects/{projectId}/github-integration", projectId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"acme\",\"repository\":\"releaseflow\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        mockMvc.perform(put("/api/projects/{projectId}/github-integration/token", projectId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"github_pat_ai_test\"}"))
                .andExpect(status().isNoContent());
        GITHUB.reset();
        String body = integration.getResponse().getContentAsString();
        return new Repository(
                projectId,
                JsonPath.read(body, "$.webhookPath"),
                JsonPath.read(body, "$.webhookSecret"),
                session
        );
    }

    private record Repository(UUID projectId, String webhookPath, String secret, MockHttpSession session) {
    }
}
