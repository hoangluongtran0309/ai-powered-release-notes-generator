package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.hoangluongtran0309.releaseflow.support.TestCategories;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GitHubWebhookIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String MERGE_SHA = "0123456789abcdef0123456789abcdef01234567";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private ChangeRepository changeRepository;

    @Autowired
    private ChangeProcessingJobRepository jobRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM change_processing_jobs");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM integration_sources");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void recordsSignedMergedPullRequestForTheVerifiedIntegration() throws Exception {
        ConnectedRepository repository = connect("owner@example.com", "Acme", "ReleaseFlow");
        UUID deliveryId = UUID.randomUUID();

        deliver(repository, "pull_request", pullRequest("acme/releaseflow", 42, "closed", true), deliveryId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("recorded"));

        assertThat(changeRepository.count()).isOne();
        Change change = changeRepository.findAll().getFirst();
        assertThat(change.getOrganizationId()).isEqualTo(repository.organizationId());
        assertThat(change.getProjectId()).isEqualTo(repository.projectId());
        assertThat(change.getPullRequestNumber()).isEqualTo(42);
        assertThat(change.getTitle()).isEqualTo("Thêm xuất CSV cho bảng điều khiển");
        assertThat(change.getDescription()).isEqualTo("Users can download any table as CSV.");
        assertThat(change.getAuthorLogin()).isEqualTo("mai-dev");
        assertThat(change.getLabels()).containsExactly("feature", "ui");
        assertThat(change.getTargetBranch()).isEqualTo("main");
        assertThat(change.getMergeCommitSha()).isEqualTo(MERGE_SHA);
        assertThat(change.getMergedAt()).isEqualTo(Instant.parse("2026-09-10T09:14:22Z"));
        assertThat(change.getUrl()).isEqualTo("https://github.com/acme/releaseflow/pull/42");
        assertThat(change.getDeliveryId()).isEqualTo(deliveryId);
        assertThat(change.getReceivedAt()).isNotNull();
        // Classification waits for the change processing worker.
        assertThat(change.getProcessingStatus()).isEqualTo(ProcessingStatus.PROCESSING);
        assertThat(change.getCategory()).isEqualTo(TestCategories.UNKNOWN);
        assertThat(change.isNeedsReview()).isTrue();
        assertThat(change.getClassificationReasons()).containsExactly("Waiting for changed files");
        assertThat(change.getReviewTriggers()).isEmpty();
        assertThat(jobRepository.findByChangeId(change.getId())).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(ChangeProcessingJob.Status.PENDING);
            assertThat(job.getAttempts()).isZero();
            assertThat(job.getOrganizationId()).isEqualTo(repository.organizationId());
            assertThat(job.getProjectId()).isEqualTo(repository.projectId());
        });

        mockMvc.perform(get("/api/projects").session(repository.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sources[0].lastDeliveryAt", notNullValue()));
        mockMvc.perform(get("/projects").session(repository.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Signed deliveries arriving")))
                .andExpect(content().string(matchesPattern(
                        "(?s).*Last delivery</dt>\\s*<dd[^>]*>\\s*<time[^>]*>\\d{1,2} [A-Z][a-z]{2} \\d{4}, \\d{2}:\\d{2} UTC</time>.*"
                )));
    }

    @Test
    void answersPingAndIgnoresUnmergedOrOtherEvents() throws Exception {
        ConnectedRepository repository = connect("owner@example.com", "acme", "releaseflow");
        String fullName = "acme/releaseflow";

        deliver(repository, "ping", ping(fullName), UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("pong"));
        deliver(repository, "ping", "{\"zen\":\"Design for failure.\",\"hook_id\":7}", UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("pong"));
        deliver(repository, "pull_request", pullRequest(fullName, 1, "closed", false), UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ignored"));
        deliver(repository, "pull_request", pullRequest(fullName, 2, "opened", false), UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ignored"));
        deliver(repository, "push", "{\"ref\":\"refs/heads/main\",\"repository\":{\"full_name\":\"acme/releaseflow\"}}",
                UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ignored"));

        assertThat(changeRepository.count()).isZero();
        mockMvc.perform(get("/api/projects").session(repository.session()))
                .andExpect(jsonPath("$[0].sources[0].lastDeliveryAt", notNullValue()));
    }

    @Test
    void rejectsMissingOrInvalidSignaturesAndUnknownWebhooksAlike() throws Exception {
        ConnectedRepository repository = connect("owner@example.com", "acme", "releaseflow");
        String body = pullRequest("acme/releaseflow", 42, "closed", true);

        expectUnauthorized(webhook(repository.webhookPath(), "pull_request", body, null, UUID.randomUUID()));
        expectUnauthorized(webhook(repository.webhookPath(), "pull_request", body,
                sign("another-secret", body), UUID.randomUUID()));
        expectUnauthorized(webhook(repository.webhookPath(), "pull_request", body.replace("42", "43"),
                sign(repository.secret(), body), UUID.randomUUID()));
        expectUnauthorized(webhook(repository.webhookPath(), "pull_request", body,
                sign(repository.secret(), body).replace("sha256=", "sha1="), UUID.randomUUID()));
        expectUnauthorized(webhook(repository.webhookPath(), "pull_request", body,
                "sha256=" + "zz".repeat(32), UUID.randomUUID()));
        expectUnauthorized(webhook("/webhooks/github/" + UUID.randomUUID(), "pull_request", body,
                sign(repository.secret(), body), UUID.randomUUID()));
        expectUnauthorized(webhook("/webhooks/github/not-a-webhook-id", "pull_request", body,
                sign(repository.secret(), body), UUID.randomUUID()));

        assertThat(changeRepository.count()).isZero();
        mockMvc.perform(get("/api/projects").session(repository.session()))
                .andExpect(jsonPath("$[0].sources[0].lastDeliveryAt", nullValue()));
    }

    @Test
    void rejectsDeliveriesNamingAnotherRepository() throws Exception {
        ConnectedRepository repository = connect("owner@example.com", "acme", "releaseflow");

        deliver(repository, "pull_request", pullRequest("acme/other", 42, "closed", true), UUID.randomUUID())
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("webhook_repository_mismatch"));
        deliver(repository, "ping", ping("acme/other"), UUID.randomUUID())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("webhook_repository_mismatch"));
        deliver(repository, "pull_request",
                pullRequest("acme/releaseflow", 42, "closed", true).replace(",\"repository\":{\"full_name\":\"acme/releaseflow\"}", ""),
                UUID.randomUUID())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("webhook_repository_mismatch"));
        assertThat(changeRepository.count()).isZero();

        deliver(repository, "pull_request", pullRequest("Acme/ReleaseFlow", 42, "closed", true), UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("recorded"));
        assertThat(changeRepository.count()).isOne();
    }

    @Test
    void recordsEachMergedPullRequestOnceAcrossRedeliveries() throws Exception {
        ConnectedRepository repository = connect("owner@example.com", "acme", "releaseflow");
        String body = pullRequest("acme/releaseflow", 42, "closed", true);
        UUID firstDelivery = UUID.randomUUID();

        deliver(repository, "pull_request", body, firstDelivery)
                .andExpect(jsonPath("$.outcome").value("recorded"));
        deliver(repository, "pull_request", body, firstDelivery)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("duplicate"));
        deliver(repository, "pull_request", body.replace("Thêm xuất CSV", "Retitled"), UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("duplicate"));

        assertThat(changeRepository.findAll()).singleElement()
                .satisfies(change -> {
                    assertThat(change.getDeliveryId()).isEqualTo(firstDelivery);
                    assertThat(change.getTitle()).startsWith("Thêm xuất CSV");
                });
    }

    @Test
    void takesTenantIdentityOnlyFromTheVerifiedIntegration() throws Exception {
        ConnectedRepository first = connect("first@example.com", "acme", "releaseflow");
        ConnectedRepository second = connect("second@example.com", "ACME", "RELEASEFLOW");
        String body = pullRequest("acme/releaseflow", 1, "closed", true)
                .replaceFirst("\\{", "{\"organization_id\":\"%s\",\"project_id\":\"%s\","
                        .formatted(second.organizationId(), second.projectId()));

        expectUnauthorized(webhook(second.webhookPath(), "pull_request", body,
                sign(first.secret(), body), UUID.randomUUID()));
        assertThat(changeRepository.count()).isZero();
        mockMvc.perform(get("/api/projects").session(second.session()))
                .andExpect(jsonPath("$[0].sources[0].lastDeliveryAt", nullValue()));

        deliver(first, "pull_request", body, UUID.randomUUID())
                .andExpect(jsonPath("$.outcome").value("recorded"));
        assertThat(changeRepository.findAll()).singleElement()
                .satisfies(change -> {
                    assertThat(change.getOrganizationId()).isEqualTo(first.organizationId());
                    assertThat(change.getProjectId()).isEqualTo(first.projectId());
                });
        mockMvc.perform(get("/api/projects").session(second.session()))
                .andExpect(jsonPath("$[0].sources[0].lastDeliveryAt", nullValue()));

        deliver(second, "pull_request", body, UUID.randomUUID())
                .andExpect(jsonPath("$.outcome").value("recorded"));
        List<UUID> organizations = changeRepository.findAll().stream().map(Change::getOrganizationId).toList();
        assertThat(organizations).containsExactlyInAnyOrder(first.organizationId(), second.organizationId());
    }

    @Test
    void acceptsDeliveriesWithoutSessionOrCsrfAndRequiresJson() throws Exception {
        ConnectedRepository repository = connect("owner@example.com", "acme", "releaseflow");
        String body = ping("acme/releaseflow");

        MvcResult result = mockMvc.perform(webhook(repository.webhookPath(), "ping", body,
                        sign(repository.secret(), body), UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();

        mockMvc.perform(post(repository.webhookPath())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("X-GitHub-Event", "ping")
                        .header("X-Hub-Signature-256", sign(repository.secret(), "payload=%7B%7D"))
                        .content("payload=%7B%7D"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void rejectsMalformedVerifiedDeliveries() throws Exception {
        ConnectedRepository repository = connect("owner@example.com", "acme", "releaseflow");
        String merged = pullRequest("acme/releaseflow", 42, "closed", true);

        expectMalformed(deliver(repository, "pull_request", "{not json", UUID.randomUUID()));
        expectMalformed(deliver(repository, "pull_request", "[]", UUID.randomUUID()));
        expectMalformed(deliver(repository, "pull_request",
                merged.replace("\"merge_commit_sha\":\"" + MERGE_SHA + "\",", ""), UUID.randomUUID()));
        expectMalformed(mockMvc.perform(webhook(repository.webhookPath(), "pull_request", merged,
                sign(repository.secret(), merged), null)));
        expectMalformed(mockMvc.perform(webhook(repository.webhookPath(), null, merged,
                sign(repository.secret(), merged), UUID.randomUUID())));

        assertThat(changeRepository.count()).isZero();
        mockMvc.perform(get("/api/projects").session(repository.session()))
                .andExpect(jsonPath("$[0].sources[0].lastDeliveryAt", nullValue()));
    }

    private void expectUnauthorized(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("webhook_signature_invalid"));
    }

    private static void expectMalformed(ResultActions result) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("webhook_payload_malformed"));
    }

    private ResultActions deliver(ConnectedRepository repository, String event, String body, UUID deliveryId)
            throws Exception {
        return mockMvc.perform(webhook(repository.webhookPath(), event, body, sign(repository.secret(), body), deliveryId));
    }

    private static MockHttpServletRequestBuilder webhook(
            String path,
            String event,
            String body,
            String signature,
            UUID deliveryId
    ) {
        MockHttpServletRequestBuilder request = post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.getBytes(StandardCharsets.UTF_8));
        if (event != null) {
            request.header("X-GitHub-Event", event);
        }
        if (signature != null) {
            request.header("X-Hub-Signature-256", signature);
        }
        if (deliveryId != null) {
            request.header("X-GitHub-Delivery", deliveryId.toString());
        }
        return request;
    }

    private static String sign(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static String pullRequest(String fullName, int number, String action, boolean merged) {
        return """
                {"action":"%s","number":%d,"pull_request":{"number":%d,\
                "title":"Thêm xuất CSV cho bảng điều khiển","body":"Users can download any table as CSV.",\
                "merged":%s,"merged_at":%s,"merge_commit_sha":"%s",\
                "html_url":"https://github.com/%s/pull/%d","user":{"login":"mai-dev"},"base":{"ref":"main"},\
                "labels":[{"name":"feature"},{"name":"ui"},{"name":"feature"}]},\
                "repository":{"full_name":"%s"}}"""
                .formatted(
                        action,
                        number,
                        number,
                        merged,
                        merged ? "\"2026-09-10T09:14:22Z\"" : "null",
                        MERGE_SHA,
                        fullName.toLowerCase(),
                        number,
                        fullName
                );
    }

    private static String ping(String fullName) {
        return "{\"zen\":\"Keep it logically awesome.\",\"hook_id\":7,\"repository\":{\"full_name\":\"%s\"}}"
                .formatted(fullName);
    }

    private ConnectedRepository connect(String email, String owner, String repository) throws Exception {
        RegistrationRequest registration = new RegistrationRequest();
        registration.setOrganizationName(email);
        registration.setDisplayName(email);
        registration.setEmail(email);
        registration.setPassword("owner-password");
        RegistrationResult registered = registrationService.register(registration);
        MvcResult login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", email)
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
                        .content("{\"owner\":\"%s\",\"repository\":\"%s\"}".formatted(owner, repository)))
                .andExpect(status().isCreated())
                .andReturn();
        String body = integration.getResponse().getContentAsString();
        return new ConnectedRepository(
                registered.organizationId(),
                projectId,
                JsonPath.read(body, "$.webhookPath"),
                JsonPath.read(body, "$.webhookSecret"),
                session
        );
    }

    private record ConnectedRepository(
            UUID organizationId,
            UUID projectId,
            String webhookPath,
            String secret,
            MockHttpSession session
    ) {
    }
}
