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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What the worker does with a change from a source that has no files to report. */
class LinearProcessingIntegrationTest extends PostgreSqlIntegrationTest {

    private static final LinearStub LINEAR = LinearStub.start();
    private static final String TEAM = "11111111-1111-4111-8111-111111111111";
    private static final String WORKSPACE = "22222222-2222-4222-8222-222222222222";
    private static final String SECRET = "lin_wh_processing-secret";
    private static final String TOKEN = "lin_api_processing-test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private ChangeProcessingWorker changeWorker;

    @Autowired
    private ChangeRepository changeRepository;

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
    void anIssueWithoutARiskyWordIsClassifiedWithoutForcingReview() throws Exception {
        Connected source = connect("owner@example.com");
        deliver(source, 10, "feat: add a CSV export button", "Users can download any table.");
        restate(10, "feat: add a CSV export button", "Users can download any table.");

        assertThat(changeWorker.processOne()).isTrue();

        Change change = changeRepository.findAll().getFirst();
        assertThat(change.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        // The source has no files, which is not the same as failing to list them.
        assertThat(change.getChangedFileStatus()).isEqualTo(ChangedFileStatus.NOT_SUPPORTED);
        assertThat(change.getReviewTriggers()).isEmpty();
        assertThat(change.isNeedsReview()).isFalse();
        mockMvc.perform(get("/changes").param("project", source.projectId().toString()).session(source.session()))
                .andExpect(content().string(not(containsString("Changed files unavailable"))));
    }

    @Test
    void aRiskyWordInTheIssueForcesReview() throws Exception {
        Connected source = connect("risky@example.com");
        deliver(source, 11, "feat: add a CSV export button", "Rotates the stored credential first.");
        restate(11, "feat: add a CSV export button", "Rotates the stored credential first.");

        changeWorker.processOne();

        Change change = changeRepository.findAll().getFirst();
        assertThat(change.getReviewTriggers())
                .extracting(ReviewTrigger::type, ReviewTrigger::detail)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(ReviewTriggerType.SENSITIVE_KEYWORD, "credential"));
        assertThat(change.isNeedsReview()).isTrue();
        mockMvc.perform(get("/changes").param("project", source.projectId().toString()).session(source.session()))
                .andExpect(content().string(containsString("Sensitive word credential")));
    }

    @Test
    void linearRestatesTheIssueOntoTheChange() throws Exception {
        Connected source = connect("restated@example.com");
        deliver(source, 12, "Stale title", "Stale description");
        restate(12, "feat: the edited title", "The edited description.");

        changeWorker.processOne();

        Change change = changeRepository.findAll().getFirst();
        assertThat(change.getTitle()).isEqualTo("feat: the edited title");
        assertThat(change.getDescription()).isEqualTo("The edited description.");
        assertThat(change.getAuthorLogin()).isEqualTo("Restated Author");
        assertThat(change.getUrl()).isEqualTo("https://linear.app/acme/issue/ENG-12-restated");
        // The restated title is what the rules judged.
        assertThat(change.getCategory().code()).isEqualTo("FEATURE");
    }

    @Test
    void anIssueLinearWillNotConfirmIsRetriedAndThenSettles() throws Exception {
        Connected source = connect("unconfirmed@example.com");
        deliver(source, 13, "feat: something", "Nothing risky here.");
        LINEAR.respondWithNoIssue();

        for (int attempt = 0; attempt < ChangeProcessingWorker.MAX_ATTEMPTS; attempt++) {
            assertThat(changeWorker.processOne()).isTrue();
            makeDue();
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM change_processing_jobs", String.class)).isEqualTo("COMPLETED");

        Change change = changeRepository.findAll().getFirst();
        assertThat(change.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(change.getChangedFileStatus()).isEqualTo(ChangedFileStatus.NOT_SUPPORTED);
        // It keeps what the delivery carried, because nothing better ever arrived.
        assertThat(change.getTitle()).isEqualTo("feat: something");
    }

    private void makeDue() {
        jdbcTemplate.update("UPDATE change_processing_jobs SET next_attempt_at = ?",
                Timestamp.from(Instant.now().minusSeconds(1)));
    }

    private void restate(int number, String title, String description) {
        LINEAR.respondWithIssue(issueId(number), title, description, "Restated Author",
                "https://linear.app/acme/issue/ENG-" + number + "-restated", TEAM, WORKSPACE);
    }

    private void deliver(Connected source, int number, String title, String description) throws Exception {
        String body = """
                {"type":"Issue","action":"update","createdAt":"%s","organizationId":"%s",\
                "updatedFrom":{"stateType":"started"},\
                "data":{"id":"%s","number":%d,"title":"%s","description":"%s","stateType":"completed",\
                "teamId":"%s","creator":{"name":"Mai Tran"},"url":"https://linear.app/acme/issue/ENG-%d"}}"""
                .formatted(Instant.now(), WORKSPACE, issueId(number), number, title, description, TEAM, number);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        mockMvc.perform(post(source.webhookPath())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.getBytes(StandardCharsets.UTF_8))
                        .header("Linear-Signature", signature)
                        .header("Linear-Delivery", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
    }

    private static String issueId(int number) {
        return "33333333-3333-4333-8333-%012d".formatted(number);
    }

    private Connected connect(String email) throws Exception {
        LINEAR.respondWithTeam(TEAM, WORKSPACE);
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
        MvcResult source = mockMvc.perform(post("/api/projects/{projectId}/sources", projectId)
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"LINEAR","teamId":"%s","webhookSecret":"%s","apiToken":"%s"}"""
                                .formatted(TEAM, SECRET, TOKEN)))
                .andExpect(status().isCreated())
                .andReturn();
        LINEAR.reset();
        return new Connected(
                registered.organizationId(),
                projectId,
                JsonPath.read(source.getResponse().getContentAsString(), "$.webhookPath"),
                session
        );
    }

    private record Connected(UUID organizationId, UUID projectId, String webhookPath, MockHttpSession session) {
    }
}
