package com.hoangluongtran0309.releaseflow.automation;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A note too big for a provider is refused here rather than sent and rejected there.
 * Both executors are pointed at an address nothing answers on, so a delivery that did
 * reach the network would end UNKNOWN instead.
 */
class ActionPayloadLimitTest {

    private static final String NOWHERE = "http://localhost:1";
    private static final Duration TIMEOUT = Duration.ofMillis(200);

    @Test
    void refusesANoteLargerThanNotionAccepts() {
        NotionActionExecutor executor =
                new NotionActionExecutor(new ObjectMapper(), NOWHERE, "2026-03-11", TIMEOUT);

        ActionResult result = executor.execute(command(
                "x".repeat(NotionActionExecutor.MAX_CONTENT_BYTES + 1),
                Map.of(NotionParentPage.KEY, AutomationIntegrationTestBase.NOTION_PARENT_PAGE),
                "notion-integration-token"
        ));

        assertThat(result.outcome()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo(NotionActionExecutor.CONTENT_TOO_LARGE);
    }

    @Test
    void refusesAPageLargerThanConfluenceAccepts() {
        ConfluenceActionExecutor executor =
                new ConfluenceActionExecutor(new ObjectMapper(), new ConfluenceSite(), NOWHERE, TIMEOUT);

        ActionResult result = executor.execute(command(
                "x".repeat(ConfluenceActionExecutor.MAX_STORAGE_BYTES + 1),
                Map.of(
                        ConfluenceSite.KEY, "https://acme.atlassian.net",
                        ConfluenceActionExecutor.EMAIL_KEY, "releases@example.com",
                        ConfluenceActionExecutor.SPACE_KEY, "42"
                ),
                "confluence-api-token"
        ));

        assertThat(result.outcome()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo(ConfluenceActionExecutor.CONTENT_TOO_LARGE);
    }

    @Test
    void failsAnActionWhoseConfigurationNoLongerHolds() {
        ConfluenceActionExecutor executor =
                new ConfluenceActionExecutor(new ObjectMapper(), new ConfluenceSite(), NOWHERE, TIMEOUT);

        // A site that changed in the database is checked again, and stops the delivery.
        ActionResult result = executor.execute(command(
                "Anything.",
                Map.of(
                        ConfluenceSite.KEY, "https://acme.atlassian.net.evil.test",
                        ConfluenceActionExecutor.EMAIL_KEY, "releases@example.com",
                        ConfluenceActionExecutor.SPACE_KEY, "42"
                ),
                "confluence-api-token"
        ));

        assertThat(result.outcome()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo(ConfluenceActionExecutor.CONFIGURATION_INVALID);
    }

    private static ActionCommand command(String note, Map<String, String> configuration, String secret) {
        return new ActionCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "1.4.0",
                "End user",
                "en",
                note,
                configuration,
                secret
        );
    }
}
