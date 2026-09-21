package com.hoangluongtran0309.releaseflow.configuration;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The management boundary, proven on real ports rather than described. Actuator answers
 * on its own port and serves exactly two things there; the application port serves none of
 * it, which is what lets a deployment publish 8080 and keep the management port private.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "management.server.port=0",
            "management.server.address=127.0.0.1"
        })
@Import(PostgreSqlTestConfiguration.class)
class ManagementPortIntegrationTest {

    private static final String TEST_MASTER_KEY = generateTestMasterKey();

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @LocalServerPort
    private int applicationPort;

    @LocalManagementPort
    private int managementPort;

    @DynamicPropertySource
    static void credentialProperties(DynamicPropertyRegistry registry) {
        registry.add("releaseflow.credentials.master-key", () -> TEST_MASTER_KEY);
        registry.add("releaseflow.processing.enabled", () -> "false");
        registry.add("releaseflow.translation.worker-enabled", () -> "false");
        registry.add("releaseflow.automation.worker-enabled", () -> "false");
        registry.add("releaseflow.automation.trigger-worker-enabled", () -> "false");
    }

    @Test
    void servesHealthAndMetricsOnTheManagementPortAndNothingElse() throws Exception {
        HttpResponse<String> health = get(managementPort, "/actuator/health");
        assertThat(health.statusCode()).isEqualTo(200);
        // Details would describe the database to anybody who reached the port.
        assertThat(health.body()).contains("\"status\":\"UP\"").doesNotContain("components", "db");

        HttpResponse<String> metrics = get(managementPort, "/actuator/prometheus");
        assertThat(metrics.statusCode()).isEqualTo(200);
        // Present before anything has happened, because every series is registered at zero.
        assertThat(metrics.body())
                .contains("releaseflow_classification_completed_total")
                .contains("releaseflow_classification_collect_to_complete_seconds_count")
                .contains("releaseflow_classification_provider_requests_total")
                .contains("releaseflow_automation_action_executions_total");

        // Everything else the actuator could offer is refused, whether it is exposed or not.
        assertThat(get(managementPort, "/actuator/env").statusCode()).isNotEqualTo(200);
        assertThat(get(managementPort, "/actuator/beans").statusCode()).isNotEqualTo(200);
        assertThat(get(managementPort, "/actuator").statusCode()).isNotEqualTo(200);
    }

    @Test
    void neverServesTheActuatorOnTheApplicationPort() throws Exception {
        // The application chain refuses these before they could ever be routed, so an
        // unauthenticated caller on the public port learns nothing and reads nothing.
        HttpResponse<String> metrics = get(applicationPort, "/actuator/prometheus");
        assertThat(metrics.statusCode()).isNotEqualTo(200);
        assertThat(metrics.body()).doesNotContain("releaseflow_classification_completed_total");
        assertThat(get(applicationPort, "/actuator/health").statusCode()).isNotEqualTo(200);

        // The public status endpoint is unaffected and still answers.
        assertThat(get(applicationPort, "/api/status").statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> get(int port, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String generateTestMasterKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
