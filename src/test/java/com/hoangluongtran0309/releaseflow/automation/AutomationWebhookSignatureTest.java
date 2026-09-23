package com.hoangluongtran0309.releaseflow.automation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AutomationWebhookSignatureTest {

    private static final String SECRET = "a-secret-nobody-else-has";
    private static final String TIMESTAMP = "1789200000";
    private static final String DELIVERY = "6a1f6d6e-0a26-4f0a-9a2e-8f9b0f0c1d2e";
    private static final String PATH = "/webhooks/automation/2b2a4c48-0b1a-4b7e-9f2a-2f9a6c1d4e5f";

    @Test
    void signsTheMomentTheDeliveryTheMethodThePathAndTheBody() {
        String signature = signature("POST", PATH, "{\"releaseId\":\"one\"}");

        assertThat(signature).startsWith("sha256=");
        // Changing any one of the five lines changes the signature.
        assertThat(signature).isNotEqualTo(signature("GET", PATH, "{\"releaseId\":\"one\"}"));
        assertThat(signature).isNotEqualTo(signature("POST", PATH + "x", "{\"releaseId\":\"one\"}"));
        assertThat(signature).isNotEqualTo(signature("POST", PATH, "{\"releaseId\":\"two\"}"));
        assertThat(signature).isNotEqualTo(AutomationWebhookSignature.of(
                SECRET, "1789200001", DELIVERY, "POST", PATH, body("{\"releaseId\":\"one\"}")));
        assertThat(signature).isNotEqualTo(AutomationWebhookSignature.of(
                "another-secret", TIMESTAMP, DELIVERY, "POST", PATH, body("{\"releaseId\":\"one\"}")));
    }

    @Test
    void readsTheMethodWhateverCaseItArrivesIn() {
        assertThat(signature("post", PATH, "{}")).isEqualTo(signature("POST", PATH, "{}"));
    }

    @Test
    void matchesOnlyTheSignatureItMade() {
        String signature = signature("POST", PATH, "{}");

        assertThat(AutomationWebhookSignature.matches(signature, signature)).isTrue();
        assertThat(AutomationWebhookSignature.matches(signature, " " + signature + " ")).isTrue();
        assertThat(AutomationWebhookSignature.matches(signature, null)).isFalse();
        assertThat(AutomationWebhookSignature.matches(signature, "")).isFalse();
        assertThat(AutomationWebhookSignature.matches(signature, signature.replace("sha256=", ""))).isFalse();
    }

    private static String signature(String method, String path, String body) {
        return AutomationWebhookSignature.of(SECRET, TIMESTAMP, DELIVERY, method, path, body(body));
    }

    private static byte[] body(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }
}
