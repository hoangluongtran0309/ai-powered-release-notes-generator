package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
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
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GitLabWebhookIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String MERGE_SHA = "0123456789abcdef0123456789abcdef01234567";
    private static final String SIGNING = "GITLAB_SIGNING_TOKEN";
    private static final String SECRET_TOKEN = "GITLAB_SECRET_TOKEN";

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
    void recordsAMergedMergeRequestSignedWithStandardWebhookHeaders() throws Exception {
        ConnectedProject project = connect("owner@example.com", "acme/group/app", SIGNING);
        UUID eventUuid = UUID.randomUUID();

        signedDelivery(project, mergeRequest("acme/group/app", 42, "merge"), eventUuid)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("recorded"));

        Change change = changeRepository.findAll().getFirst();
        assertThat(change.getOrganizationId()).isEqualTo(project.organizationId());
        assertThat(change.getProjectId()).isEqualTo(project.projectId());
        assertThat(change.getPullRequestNumber()).isEqualTo(42);
        assertThat(change.getTitle()).isEqualTo("Thêm xuất CSV cho bảng điều khiển");
        assertThat(change.getAuthorLogin()).isEqualTo("mai-dev");
        assertThat(change.getLabels()).containsExactly("feature", "ui");
        assertThat(change.getTargetBranch()).isEqualTo("main");
        assertThat(change.getMergeCommitSha()).isEqualTo(MERGE_SHA);
        assertThat(change.getUrl()).isEqualTo("https://gitlab.com/acme/group/app/-/merge_requests/42");
        assertThat(change.getMergedAt()).isEqualTo(Instant.parse("2026-09-10T09:14:22Z"));
        assertThat(jobRepository.findAll()).hasSize(1);
    }

    @Test
    void recordsAMergedMergeRequestProvedWithTheSecretTokenHeader() throws Exception {
        ConnectedProject project = connect("token@example.com", "acme/app", SECRET_TOKEN);

        mockMvc.perform(delivery(project.webhookPath(), mergeRequest("acme/app", 8, "merge"))
                        .header("X-Gitlab-Token", project.secret()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("recorded"));

        assertThat(changeRepository.findAll()).hasSize(1);
    }

    @Test
    void acceptsAnyOfSeveralSignaturesAndAWhsecPrefixedSecret() throws Exception {
        ConnectedProject project = connect("rotating@example.com", "acme/app", SIGNING);
        String body = mergeRequest("acme/app", 9, "merge");
        String deliveryId = UUID.randomUUID().toString();
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String valid = standardSignature("whsec_" + project.secret(), deliveryId, timestamp, body);

        mockMvc.perform(delivery(project.webhookPath(), body)
                        .header("webhook-id", deliveryId)
                        .header("webhook-timestamp", timestamp)
                        .header("webhook-signature", "v1,AAAA " + valid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("recorded"));
    }

    @Test
    void refusesADeliverySignedOutsideTheAllowedSkew() throws Exception {
        ConnectedProject project = connect("stale@example.com", "acme/app", SIGNING);
        String body = mergeRequest("acme/app", 10, "merge");

        for (long offset : List.of(-301L, 301L)) {
            String deliveryId = UUID.randomUUID().toString();
            String timestamp = Long.toString(Instant.now().getEpochSecond() + offset);
            mockMvc.perform(delivery(project.webhookPath(), body)
                            .header("webhook-id", deliveryId)
                            .header("webhook-timestamp", timestamp)
                            .header("webhook-signature", standardSignature(project.secret(), deliveryId, timestamp, body)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("webhook_signature_invalid"));
        }

        String deliveryId = UUID.randomUUID().toString();
        String fresh = Long.toString(Instant.now().getEpochSecond() - 299);
        mockMvc.perform(delivery(project.webhookPath(), body)
                        .header("webhook-id", deliveryId)
                        .header("webhook-timestamp", fresh)
                        .header("webhook-signature", standardSignature(project.secret(), deliveryId, fresh, body)))
                .andExpect(status().isOk());
        assertThat(changeRepository.findAll()).hasSize(1);
    }

    @Test
    void refusesEveryUnprovenDeliveryTheSameWay() throws Exception {
        ConnectedProject signing = connect("signing@example.com", "acme/app", SIGNING);
        ConnectedProject secretToken = connect("secret@example.com", "acme/other", SECRET_TOKEN);
        String body = mergeRequest("acme/app", 11, "merge");
        String deliveryId = UUID.randomUUID().toString();
        String timestamp = Long.toString(Instant.now().getEpochSecond());

        // No proof at all.
        expectUnauthorized(delivery(signing.webhookPath(), body));
        // Standard Webhooks headers, one at a time, are not enough.
        expectUnauthorized(delivery(signing.webhookPath(), body).header("webhook-id", deliveryId));
        expectUnauthorized(delivery(signing.webhookPath(), body)
                .header("webhook-id", deliveryId)
                .header("webhook-timestamp", timestamp));
        // A signature over other bytes.
        expectUnauthorized(delivery(signing.webhookPath(), body)
                .header("webhook-id", deliveryId)
                .header("webhook-timestamp", timestamp)
                .header("webhook-signature", standardSignature(signing.secret(), deliveryId, timestamp, "{}")));
        // Another source's secret.
        expectUnauthorized(delivery(signing.webhookPath(), body)
                .header("webhook-id", deliveryId)
                .header("webhook-timestamp", timestamp)
                .header("webhook-signature", standardSignature(secretToken.secret(), deliveryId, timestamp, body)));
        // The wrong mode for this source.
        expectUnauthorized(delivery(signing.webhookPath(), body).header("X-Gitlab-Token", signing.secret()));
        expectUnauthorized(delivery(secretToken.webhookPath(), body)
                .header("webhook-id", deliveryId)
                .header("webhook-timestamp", timestamp)
                .header("webhook-signature", standardSignature(secretToken.secret(), deliveryId, timestamp, body)));
        // A wrong or missing secret token.
        expectUnauthorized(delivery(secretToken.webhookPath(), body).header("X-Gitlab-Token", "not-the-secret"));
        // An unknown webhook ID, and one that is not a UUID.
        expectUnauthorized(delivery("/webhooks/gitlab/" + UUID.randomUUID(), body)
                .header("X-Gitlab-Token", secretToken.secret()));
        expectUnauthorized(delivery("/webhooks/gitlab/not-a-uuid", body).header("X-Gitlab-Token", secretToken.secret()));

        assertThat(changeRepository.findAll()).isEmpty();
    }

    @Test
    void refusesAGitHubSourceOnTheGitLabPath() throws Exception {
        ConnectedProject gitHub = connectGitHub("github@example.com");

        expectUnauthorized(delivery("/webhooks/gitlab/" + gitHub.webhookId(), mergeRequest("acme/app", 12, "merge"))
                .header("X-Gitlab-Token", gitHub.secret()));
    }

    @Test
    void refusesASignedDeliveryNamingAnotherProject() throws Exception {
        ConnectedProject project = connect("mismatch@example.com", "acme/app", SIGNING);

        signedDelivery(project, mergeRequest("acme/other", 13, "merge"), UUID.randomUUID())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("webhook_repository_mismatch"));

        assertThat(changeRepository.findAll()).isEmpty();
    }

    @Test
    void acknowledgesEventsThatAreNotAMergedMergeRequest() throws Exception {
        ConnectedProject project = connect("ignored@example.com", "acme/app", SIGNING);

        for (String action : List.of("open", "update", "close", "approved")) {
            signedDelivery(project, mergeRequest("acme/app", 14, action), UUID.randomUUID())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.outcome").value("ignored"));
        }
        // Another kind of event does not even have to name the project.
        signedDelivery(project, "{\"object_kind\":\"push\",\"project\":{\"path_with_namespace\":\"someone/else\"}}",
                UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ignored"));

        assertThat(changeRepository.findAll()).isEmpty();
    }

    @Test
    void recordsAMergeRequestOnceHoweverOftenItIsDelivered() throws Exception {
        ConnectedProject project = connect("repeat@example.com", "acme/app", SIGNING);
        String body = mergeRequest("acme/app", 15, "merge");

        signedDelivery(project, body, UUID.randomUUID()).andExpect(jsonPath("$.outcome").value("recorded"));
        signedDelivery(project, body, UUID.randomUUID()).andExpect(jsonPath("$.outcome").value("duplicate"));

        assertThat(changeRepository.findAll()).hasSize(1);
        assertThat(jobRepository.findAll()).hasSize(1);
    }

    @Test
    void refusesADeliveryLargerThanAnyProviderSends() throws Exception {
        ConnectedProject project = connect("large@example.com", "acme/app", SIGNING);
        String body = "{\"object_kind\":\"merge_request\",\"filler\":\"" + "x".repeat(1_048_576) + "\"}";

        signedDelivery(project, body, UUID.randomUUID())
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("webhook_payload_too_large"));
    }

    @Test
    void takesTenantIdentityOnlyFromTheVerifiedSource() throws Exception {
        ConnectedProject project = connect("tenant@example.com", "acme/app", SIGNING);
        String body = mergeRequest("acme/app", 16, "merge")
                .replace("\"object_kind\"", "\"organization_id\":\"%s\",\"project_id\":\"%s\",\"object_kind\""
                        .formatted(UUID.randomUUID(), UUID.randomUUID()));

        signedDelivery(project, body, UUID.randomUUID()).andExpect(status().isOk());

        Change change = changeRepository.findAll().getFirst();
        assertThat(change.getOrganizationId()).isEqualTo(project.organizationId());
        assertThat(change.getProjectId()).isEqualTo(project.projectId());
    }

    @Test
    void recordsTheEventUuidAsTheDeliveryOnlyWhenItIsOne() throws Exception {
        ConnectedProject project = connect("delivery@example.com", "acme/app", SIGNING);
        UUID eventUuid = UUID.randomUUID();

        signedDelivery(project, mergeRequest("acme/app", 17, "merge"), eventUuid).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT delivery_id FROM changes", UUID.class)).isEqualTo(eventUuid);

        String body = mergeRequest("acme/app", 18, "merge");
        String deliveryId = UUID.randomUUID().toString();
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        mockMvc.perform(delivery(project.webhookPath(), body)
                        .header("webhook-id", deliveryId)
                        .header("webhook-timestamp", timestamp)
                        .header("webhook-signature", standardSignature(project.secret(), deliveryId, timestamp, body))
                        .header("X-Gitlab-Event-UUID", "not-a-uuid"))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT delivery_id FROM changes WHERE pull_request_number = 18", UUID.class)).isNull();
    }

    @Test
    void acceptsDeliveriesWithoutSessionOrCsrfAndRequiresJson() throws Exception {
        ConnectedProject project = connect("plain@example.com", "acme/app", SECRET_TOKEN);

        mockMvc.perform(post(project.webhookPath())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("object_kind=merge_request")
                        .header("X-Gitlab-Token", project.secret()))
                .andExpect(status().isUnsupportedMediaType());

        mockMvc.perform(delivery(project.webhookPath(), mergeRequest("acme/app", 19, "merge"))
                        .header("X-Gitlab-Token", project.secret()))
                .andExpect(status().isOk());
    }

    @Test
    void refusesASignedButUnreadablePayload() throws Exception {
        ConnectedProject project = connect("malformed@example.com", "acme/app", SIGNING);

        signedDelivery(project, "not json", UUID.randomUUID())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("webhook_payload_malformed"));
        signedDelivery(project, mergeRequest("acme/app", 20, "merge").replace("\"iid\":20", "\"iid\":0"),
                UUID.randomUUID())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("webhook_payload_malformed"));
        signedDelivery(project, mergeRequest("acme/app", 21, "merge").replace("\"target_branch\":\"main\",", ""),
                UUID.randomUUID())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("webhook_payload_malformed"));

        assertThat(changeRepository.findAll()).isEmpty();
    }

    @Test
    void readsAMergeRequestWithoutAMergeCommitOrMergeTime() throws Exception {
        ConnectedProject project = connect("squashed@example.com", "acme/app", SIGNING);
        String squashSha = "fedcba9876543210fedcba9876543210fedcba98";
        String body = mergeRequest("acme/app", 22, "merge")
                .replace("\"merge_commit_sha\":\"%s\",".formatted(MERGE_SHA),
                        "\"merge_commit_sha\":null,\"squash_commit_sha\":\"%s\",".formatted(squashSha))
                .replace("\"merged_at\":\"2026-09-10T09:14:22Z\",", "\"updated_at\":\"2026-09-10 09:14:22 UTC\",");

        signedDelivery(project, body, UUID.randomUUID()).andExpect(status().isOk());

        Change change = changeRepository.findAll().getFirst();
        assertThat(change.getMergeCommitSha()).isEqualTo(squashSha);
        assertThat(change.getMergedAt()).isEqualTo(Instant.parse("2026-09-10T09:14:22Z"));
    }

    private void expectUnauthorized(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("webhook_signature_invalid"));
    }

    private ResultActions signedDelivery(ConnectedProject project, String body, UUID eventUuid) throws Exception {
        String deliveryId = UUID.randomUUID().toString();
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        return mockMvc.perform(delivery(project.webhookPath(), body)
                .header("webhook-id", deliveryId)
                .header("webhook-timestamp", timestamp)
                .header("webhook-signature", standardSignature(project.secret(), deliveryId, timestamp, body))
                .header("X-Gitlab-Event", "Merge Request Hook")
                .header("X-Gitlab-Event-UUID", eventUuid.toString()));
    }

    private static MockHttpServletRequestBuilder delivery(String path, String body) {
        return post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.getBytes(StandardCharsets.UTF_8));
    }

    private static String standardSignature(String secret, String deliveryId, String timestamp, String body)
            throws Exception {
        String encoded = secret.startsWith("whsec_") ? secret.substring("whsec_".length()) : secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getUrlDecoder().decode(encoded), "HmacSHA256"));
        byte[] signature = mac.doFinal((deliveryId + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
        return "v1," + Base64.getEncoder().encodeToString(signature);
    }

    private static String mergeRequest(String projectPath, int iid, String action) {
        return """
                {"object_kind":"merge_request","user":{"username":"mai-dev"},\
                "project":{"path_with_namespace":"%s"},\
                "labels":[{"title":"feature"},{"title":"ui"},{"title":"feature"}],\
                "object_attributes":{"iid":%d,"action":"%s",\
                "title":"Thêm xuất CSV cho bảng điều khiển","description":"Users can download any table as CSV.",\
                "target_branch":"main","source_branch":"feature/csv",\
                "merge_commit_sha":"%s","merged_at":"2026-09-10T09:14:22Z",\
                "url":"https://gitlab.com/%s/-/merge_requests/%d"}}"""
                .formatted(projectPath, iid, action, MERGE_SHA, projectPath, iid);
    }

    private ConnectedProject connect(String email, String projectPath, String authMode) throws Exception {
        Session session = signIn(email);
        MvcResult source = mockMvc.perform(post("/api/projects/{projectId}/sources", session.projectId())
                        .session(session.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"GITLAB","apiBaseUrl":"https://gitlab.com","projectPath":"%s",\
                                "webhookAuthMode":"%s"}"""
                                .formatted(projectPath, authMode)))
                .andExpect(status().isCreated())
                .andReturn();
        String body = source.getResponse().getContentAsString();
        return new ConnectedProject(
                session.organizationId(),
                session.projectId(),
                JsonPath.read(body, "$.webhookId"),
                JsonPath.read(body, "$.webhookPath"),
                JsonPath.read(body, "$.webhookSecret")
        );
    }

    private ConnectedProject connectGitHub(String email) throws Exception {
        Session session = signIn(email);
        MvcResult source = mockMvc.perform(post("/api/projects/{projectId}/sources", session.projectId())
                        .session(session.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"acme\",\"repository\":\"app\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String body = source.getResponse().getContentAsString();
        return new ConnectedProject(
                session.organizationId(),
                session.projectId(),
                JsonPath.read(body, "$.webhookId"),
                JsonPath.read(body, "$.webhookPath"),
                JsonPath.read(body, "$.webhookSecret")
        );
    }

    private Session signIn(String email) throws Exception {
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
        return new Session(
                registered.organizationId(),
                UUID.fromString(JsonPath.read(project.getResponse().getContentAsString(), "$.id")),
                session
        );
    }

    private record Session(UUID organizationId, UUID projectId, MockHttpSession session) {
    }

    private record ConnectedProject(
            UUID organizationId,
            UUID projectId,
            String webhookId,
            String webhookPath,
            String secret
    ) {
    }
}
