package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.LinearStub;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LinearWebhookIntegrationTest extends PostgreSqlIntegrationTest {

    private static final LinearStub LINEAR = LinearStub.start();
    private static final String TEAM = "11111111-1111-4111-8111-111111111111";
    private static final String WORKSPACE = "22222222-2222-4222-8222-222222222222";
    private static final String SECRET = "lin_wh_a-secret-linear-generated";
    private static final String TOKEN = "lin_api_webhook-test";

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

    @DynamicPropertySource
    static void linearProperties(DynamicPropertyRegistry registry) {
        registry.add("releaseflow.linear.api-base-url", LINEAR::baseUrl);
        registry.add("releaseflow.linear.timeout", () -> "PT2S");
    }

    @AfterAll
    static void stopStub() {
        LINEAR.close();
    }

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        LINEAR.reset();
        jdbcTemplate.update("DELETE FROM change_processing_jobs");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM integration_sources");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void recordsAnIssueThatJustMovedIntoACompletedState() throws Exception {
        Connected source = connect("owner@example.com");
        Instant completedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        deliver(source, issue(TEAM, 123, "started", "completed", quoted(completedAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("recorded"));

        Change change = changeRepository.findAll().getFirst();
        assertThat(change.getOrganizationId()).isEqualTo(source.organizationId());
        assertThat(change.getProjectId()).isEqualTo(source.projectId());
        // The number a person sees, and the UUID the source names it by.
        assertThat(change.getPullRequestNumber()).isEqualTo(123);
        assertThat(change.getTitle()).isEqualTo("Rotate the export credential");
        assertThat(change.getAuthorLogin()).isEqualTo("Mai Tran");
        assertThat(change.getUrl()).isEqualTo("https://linear.app/acme/issue/ENG-123");
        assertThat(change.getMergedAt()).isEqualTo(completedAt);
        // An issue has neither of these, and the database now agrees.
        assertThat(change.getMergeCommitSha()).isNull();
        assertThat(change.getTargetBranch()).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT external_id FROM changes", String.class))
                .isEqualTo(issueId(123));
        assertThat(jdbcTemplate.queryForObject("SELECT source_type FROM changes", String.class)).isEqualTo("LINEAR");
        assertThat(jdbcTemplate.queryForObject("SELECT delivery_id FROM changes", UUID.class)).isNull();
        assertThat(jobRepository.findAll()).hasSize(1);
    }

    @Test
    void ignoresEverythingThatIsNotACompletion() throws Exception {
        Connected source = connect("ignored@example.com");

        // Already completed, so nothing became done here.
        deliver(source, issue(TEAM, 1, "completed", "completed"))
                .andExpect(jsonPath("$.outcome").value("ignored"));
        // Moved somewhere other than completed.
        deliver(source, issue(TEAM, 2, "started", "canceled"))
                .andExpect(jsonPath("$.outcome").value("ignored"));
        // The state did not change at all.
        deliver(source, issue(TEAM, 3, null, "completed"))
                .andExpect(jsonPath("$.outcome").value("ignored"));
        // Another kind of thing, and another kind of action.
        deliver(source, issue(TEAM, 4, "started", "completed").replace("\"Issue\"", "\"Comment\""))
                .andExpect(jsonPath("$.outcome").value("ignored"));
        deliver(source, issue(TEAM, 5, "started", "completed").replace("\"update\"", "\"create\""))
                .andExpect(jsonPath("$.outcome").value("ignored"));

        assertThat(changeRepository.findAll()).isEmpty();
    }

    @Test
    void refusesEveryUnprovenDeliveryTheSameWay() throws Exception {
        Connected source = connect("signed@example.com");
        Connected other = connect("other@example.com", "lin_wh_a-different-secret");
        String body = issue(TEAM, 6, "started", "completed");

        // No proof at all, and each header alone.
        expectUnauthorized(request(source.webhookPath(), body));
        expectUnauthorized(request(source.webhookPath(), body).header("Linear-Signature", sign(SECRET, body)));
        expectUnauthorized(request(source.webhookPath(), body).header("Linear-Delivery", "delivery-1"));
        // A signature over other bytes, and another source's secret.
        expectUnauthorized(signed(source.webhookPath(), body, sign(SECRET, "{}")));
        expectUnauthorized(signed(source.webhookPath(), body, sign("another-secret", body)));
        // An unknown webhook ID, and one that is not a UUID.
        expectUnauthorized(signed("/webhooks/linear/" + UUID.randomUUID(), body, sign(SECRET, body)));
        expectUnauthorized(signed("/webhooks/linear/not-a-uuid", body, sign(SECRET, body)));
        // Another tenant's Linear source has its own secret, which this body was not signed with.
        expectUnauthorized(signed(other.webhookPath(), body, sign(SECRET, body)));

        assertThat(changeRepository.findAll()).isEmpty();
    }

    @Test
    void refusesADeliveryStampedOutsideTheAllowedSkew() throws Exception {
        Connected source = connect("stale@example.com");

        // The window is a minute. These sit well outside and well inside it, rather than
        // a second either side of the boundary, so a slow machine cannot move a stamp
        // across it while the request is in flight.
        for (long offset : List.of(-90L, 90L)) {
            String body = issue(TEAM, 7, "started", "completed", quoted(Instant.now().plusSeconds(offset)));
            expectUnauthorized(signed(source.webhookPath(), body, sign(SECRET, body)));
        }
        String fresh = issue(TEAM, 8, "started", "completed", quoted(Instant.now().minusSeconds(30)));
        mockMvc.perform(signed(source.webhookPath(), fresh, sign(SECRET, fresh))).andExpect(status().isOk());

        // Linear may stamp the delivery with epoch milliseconds instead.
        String millis = issue(TEAM, 9, "started", "completed", Long.toString(Instant.now().toEpochMilli()));
        mockMvc.perform(signed(source.webhookPath(), millis, sign(SECRET, millis))).andExpect(status().isOk());
        String stale = issue(TEAM, 10, "started", "completed",
                Long.toString(Instant.now().minusSeconds(120).toEpochMilli()));
        expectUnauthorized(signed(source.webhookPath(), stale, sign(SECRET, stale)));

        assertThat(changeRepository.findAll()).hasSize(2);
    }

    @Test
    void refusesADeliveryFromAnotherWorkspace() throws Exception {
        Connected source = connect("workspace@example.com");
        String body = issue(TEAM, 11, "started", "completed")
                .replace(WORKSPACE, "99999999-9999-4999-8999-999999999999");

        expectUnauthorized(signed(source.webhookPath(), body, sign(SECRET, body)));

        assertThat(changeRepository.findAll()).isEmpty();
    }

    @Test
    void refusesAProvenDeliveryNamingAnotherTeam() throws Exception {
        Connected source = connect("team@example.com");

        deliver(source, issue("44444444-4444-4444-8444-444444444444", 12, "started", "completed"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("webhook_repository_mismatch"));

        assertThat(changeRepository.findAll()).isEmpty();
    }

    @Test
    void refusesAGitHubSourceOnTheLinearPath() throws Exception {
        Connected source = connect("github@example.com");
        String gitHub = connectGitHub(source);
        String body = issue(TEAM, 13, "started", "completed");

        expectUnauthorized(signed("/webhooks/linear/" + gitHub, body, sign(SECRET, body)));
    }

    @Test
    void recordsAnIssueOnceHoweverOftenItIsDelivered() throws Exception {
        Connected source = connect("repeat@example.com");
        String body = issue(TEAM, 14, "started", "completed");

        deliver(source, body).andExpect(jsonPath("$.outcome").value("recorded"));
        deliver(source, body).andExpect(jsonPath("$.outcome").value("duplicate"));

        assertThat(changeRepository.findAll()).hasSize(1);
        assertThat(jobRepository.findAll()).hasSize(1);
    }

    @Test
    void refusesADeliveryLargerThanAnyProviderSends() throws Exception {
        Connected source = connect("large@example.com");
        String body = "{\"type\":\"Issue\",\"filler\":\"" + "x".repeat(1_048_576) + "\"}";

        mockMvc.perform(signed(source.webhookPath(), body, sign(SECRET, body)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("webhook_payload_too_large"));
    }

    @Test
    void takesTenantIdentityOnlyFromTheVerifiedSource() throws Exception {
        Connected source = connect("tenant@example.com");
        String body = issue(TEAM, 15, "started", "completed")
                .replace("\"type\":\"Issue\"", "\"organization_id\":\"%s\",\"project_id\":\"%s\",\"type\":\"Issue\""
                        .formatted(UUID.randomUUID(), UUID.randomUUID()));

        deliver(source, body).andExpect(status().isOk());

        Change change = changeRepository.findAll().getFirst();
        assertThat(change.getOrganizationId()).isEqualTo(source.organizationId());
        assertThat(change.getProjectId()).isEqualTo(source.projectId());
    }

    @Test
    void refusesAProvenButUnreadablePayload() throws Exception {
        Connected source = connect("malformed@example.com");

        deliver(source, issue(TEAM, 16, "started", "completed").replace("\"number\":16", "\"number\":0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("webhook_payload_malformed"));
        deliver(source, issue(TEAM, 17, "started", "completed").replace("\"url\":\"https://linear.app/acme/issue/ENG-17\",", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("webhook_payload_malformed"));

        assertThat(changeRepository.findAll()).isEmpty();
    }

    private void expectUnauthorized(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("webhook_signature_invalid"));
    }

    private ResultActions deliver(Connected source, String body) throws Exception {
        return mockMvc.perform(signed(source.webhookPath(), body, sign(SECRET, body)));
    }

    private static MockHttpServletRequestBuilder signed(String path, String body, String signature) {
        return request(path, body)
                .header("Linear-Signature", signature)
                .header("Linear-Delivery", UUID.randomUUID().toString());
    }

    private static MockHttpServletRequestBuilder request(String path, String body) {
        return post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.getBytes(StandardCharsets.UTF_8));
    }

    private static String sign(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static String issue(String teamId, int number, String previousState, String state) {
        return issue(teamId, number, previousState, state, quoted(Instant.now()));
    }

    /** @param createdAt the JSON value Linear stamps the delivery with, quoted or not */
    private static String issue(String teamId, int number, String previousState, String state, String createdAt) {
        String updatedFrom = previousState == null
                ? "{\"title\":\"Old title\"}"
                : "{\"stateType\":\"%s\"}".formatted(previousState);
        return """
                {"type":"Issue","action":"update","createdAt":%s,\
                "organizationId":"%s","updatedFrom":%s,\
                "data":{"id":"%s","number":%d,\
                "title":"Rotate the export credential","description":"The exporter now reads a rotated key.",\
                "stateType":"%s","teamId":"%s","creator":{"name":"Mai Tran"},\
                "url":"https://linear.app/acme/issue/ENG-%d","labels":[{"name":"security"}]}}"""
                .formatted(createdAt, WORKSPACE, updatedFrom, issueId(number), number, state, teamId, number);
    }

    private static String issueId(int number) {
        return "33333333-3333-4333-8333-%012d".formatted(number);
    }

    private static String quoted(Instant instant) {
        return "\"" + instant + "\"";
    }

    private Connected connect(String email) throws Exception {
        return connect(email, SECRET);
    }

    private Connected connect(String email, String secret) throws Exception {
        LINEAR.respondWithTeam(TEAM, WORKSPACE);
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
        MvcResult source = mockMvc.perform(post("/api/projects/{projectId}/sources", projectId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"LINEAR","teamId":"%s","webhookSecret":"%s","apiToken":"%s"}"""
                                .formatted(TEAM, secret, TOKEN)))
                .andExpect(status().isCreated())
                .andReturn();
        return new Connected(
                registered.organizationId(),
                projectId,
                JsonPath.read(source.getResponse().getContentAsString(), "$.webhookPath"),
                session
        );
    }

    private String connectGitHub(Connected source) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects/{projectId}/sources", source.projectId())
                        .session(source.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"acme\",\"repository\":\"app\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(created.getResponse().getContentAsString(), "$.webhookId");
    }

    private record Connected(UUID organizationId, UUID projectId, String webhookPath, MockHttpSession session) {
    }
}
