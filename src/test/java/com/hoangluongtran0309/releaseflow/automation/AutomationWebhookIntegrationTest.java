package com.hoangluongtran0309.releaseflow.automation;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A rule another system sets off, proving itself with the secret and nothing else. */
class AutomationWebhookIntegrationTest extends AutomationIntegrationTestBase {

    @Autowired
    private AutomationWorker worker;

    @Test
    void carriesOutASignedCallOnceHoweverOftenItIsRepeated() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        Webhook webhook = createWebhookRule(owner, projectId, endUser);
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        String body = "{\"releaseId\":\"%s\"}".formatted(releaseId);
        UUID delivery = UUID.randomUUID();

        String runId = JsonPath.read(call(webhook, delivery, body)
                .andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.statusPath").value(containsString(webhook.path() + "/runs/")))
                .andReturn().getResponse().getContentAsString(), "$.runId");

        // The same delivery, sent again after a timeout, is the same run.
        call(webhook, delivery, body)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").value(runId));
        assertThat(runCount()).isOne();

        deliverEverything();
        assertThat(SLACK.messages()).hasSize(1);

        // Asking what became of it is signed too, over the path being asked about.
        String statusPath = webhook.path() + "/runs/" + runId;
        mockMvc.perform(signedGet(webhook, statusPath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.actions[0].actionType").value("SLACK"))
                .andExpect(jsonPath("$.actions[0].status").value("SUCCEEDED"));
        mockMvc.perform(get(statusPath)).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/automation/runs").session(owner.session()))
                .andExpect(jsonPath("$.items[0].triggerType").value("EXTERNAL_WEBHOOK"))
                .andExpect(jsonPath("$.items[0].initiatorName").doesNotExist());
    }

    @Test
    void refusesACallItCannotProve() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        Webhook webhook = createWebhookRule(owner, projectId, audienceId(owner, "end_user"));
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        String body = "{\"releaseId\":\"%s\"}".formatted(releaseId);
        String now = String.valueOf(Instant.now().getEpochSecond());
        UUID delivery = UUID.randomUUID();

        // No signature at all.
        mockMvc.perform(post(webhook.path()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        // Somebody else's secret.
        mockMvc.perform(signedPost(webhook.path(), now, delivery,
                        sign("another-secret", now, delivery.toString(), "POST", webhook.path(), body), body))
                .andExpect(status().isUnauthorized());

        // The right signature, but for another body.
        mockMvc.perform(signedPost(webhook.path(), now, delivery,
                        sign(webhook.secret(), now, delivery.toString(), "POST", webhook.path(), "{}"), body))
                .andExpect(status().isUnauthorized());

        // The right signature, but made for another path.
        mockMvc.perform(signedPost(webhook.path(), now, delivery,
                        sign(webhook.secret(), now, delivery.toString(), "POST", "/webhooks/automation/elsewhere", body),
                        body))
                .andExpect(status().isUnauthorized());

        // A moment too far from this one, signed correctly for that moment.
        String stale = String.valueOf(Instant.now().minusSeconds(3600).getEpochSecond());
        mockMvc.perform(signedPost(webhook.path(), stale, delivery,
                        sign(webhook.secret(), stale, delivery.toString(), "POST", webhook.path(), body), body))
                .andExpect(status().isUnauthorized());

        // A path nobody answers.
        String unknown = "/webhooks/automation/" + UUID.randomUUID();
        mockMvc.perform(signedPost(unknown, now, delivery,
                        sign(webhook.secret(), now, delivery.toString(), "POST", unknown, body), body))
                .andExpect(status().isUnauthorized());

        assertThat(runCount()).isZero();
    }

    @Test
    void refusesABodyLargerThanOneReleaseIdentifierNeeds() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        Webhook webhook = createWebhookRule(owner, projectId, audienceId(owner, "end_user"));
        String body = "{\"releaseId\":\"%s\",\"padding\":\"%s\"}"
                .formatted(UUID.randomUUID(), "x".repeat(70_000));

        mockMvc.perform(signedPost(webhook.path(), String.valueOf(Instant.now().getEpochSecond()), UUID.randomUUID(),
                        "sha256=whatever", body))
                .andExpect(status().isPayloadTooLarge());
        assertThat(runCount()).isZero();
    }

    @Test
    void rotatingTheSecretKeepsThePathAndStopsTheOldSecretAtOnce() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        Webhook webhook = createWebhookRule(owner, projectId, audienceId(owner, "end_user"));
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        String body = "{\"releaseId\":\"%s\"}".formatted(releaseId);

        String rotated = mockMvc.perform(post("/api/automation/rules/{ruleId}/webhook-secret/rotate", webhook.ruleId())
                        .session(owner.session()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.webhookPath").value(webhook.path()))
                .andReturn().getResponse().getContentAsString();
        String newSecret = JsonPath.read(rotated, "$.webhookSecret");
        assertThat(newSecret).isNotEqualTo(webhook.secret());

        call(webhook, UUID.randomUUID(), body).andExpect(status().isUnauthorized());
        call(new Webhook(webhook.ruleId(), webhook.path(), newSecret), UUID.randomUUID(), body)
                .andExpect(status().isAccepted());

        // No later reading of the rule repeats either secret.
        mockMvc.perform(get("/api/automation/rules").session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].webhookSecretConfigured").value(true))
                .andExpect(jsonPath("$[0].webhookSecret").doesNotExist())
                .andExpect(content().string(not(containsString(newSecret))))
                .andExpect(content().string(not(containsString(webhook.secret()))));
    }

    @Test
    void answersNothingOnceTheRuleIsTurnedOff() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        Webhook webhook = createWebhookRule(owner, projectId, audienceId(owner, "end_user"));
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        String body = "{\"releaseId\":\"%s\"}".formatted(releaseId);

        mockMvc.perform(post("/api/automation/rules/{ruleId}/disable", webhook.ruleId())
                        .session(owner.session()).with(csrf()))
                .andExpect(status().isOk());

        call(webhook, UUID.randomUUID(), body).andExpect(status().isUnauthorized());
        assertThat(runCount()).isZero();
    }

    @Test
    void refusesABodyThatNamesNoReleaseOrOneThatIsNotPublished() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        Webhook webhook = createWebhookRule(owner, projectId, audienceId(owner, "end_user"));

        call(webhook, UUID.randomUUID(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("webhook_payload_malformed"));

        UUID approved = approvedReleaseWithChange(owner, projectId, "1.4.0");
        call(webhook, UUID.randomUUID(), "{\"releaseId\":\"%s\"}".formatted(approved))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("automation_release_not_published"));
        assertThat(runCount()).isZero();
    }

    private Webhook createWebhookRule(Owner owner, UUID projectId, UUID audienceId) throws Exception {
        String created = createRule(owner, webhookRule("Called from the status page", projectId, audienceId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.webhookPath").exists())
                .andExpect(jsonPath("$.webhookSecret").exists())
                .andReturn().getResponse().getContentAsString();
        UUID ruleId = UUID.fromString(JsonPath.read(created, "$.id"));
        enable(owner, ruleId).andExpect(status().isOk());
        return new Webhook(ruleId, JsonPath.read(created, "$.webhookPath"), JsonPath.read(created, "$.webhookSecret"));
    }

    private ResultActions call(Webhook webhook, UUID delivery, String body) throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        return mockMvc.perform(signedPost(
                webhook.path(),
                timestamp,
                delivery,
                sign(webhook.secret(), timestamp, delivery.toString(), "POST", webhook.path(), body),
                body
        ));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signedPost(
            String path,
            String timestamp,
            UUID delivery,
            String signature,
            String body
    ) {
        return post(path)
                .header("X-ReleaseFlow-Timestamp", timestamp)
                .header("X-ReleaseFlow-Delivery", delivery.toString())
                .header("X-ReleaseFlow-Signature-256", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signedGet(
            Webhook webhook,
            String path
    ) throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        UUID delivery = UUID.randomUUID();
        return get(path)
                .header("X-ReleaseFlow-Timestamp", timestamp)
                .header("X-ReleaseFlow-Delivery", delivery.toString())
                .header("X-ReleaseFlow-Signature-256",
                        sign(webhook.secret(), timestamp, delivery.toString(), "GET", path, ""));
    }

    // Publishing a release also left an outbox row; working everything dry leaves only
    // the deliveries this test means to count.
    private void deliverEverything() {
        while (worker.processOne()) {
            // Keep going until nothing is left.
        }
    }

    private Integer runCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM automation_runs", Integer.class);
    }

    private record Webhook(UUID ruleId, String path, String secret) {
    }
}
