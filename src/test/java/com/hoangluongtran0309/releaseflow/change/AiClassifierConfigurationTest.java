package com.hoangluongtran0309.releaseflow.change;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiClassifierConfigurationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test
    void noProviderMeansNoAi() throws Exception {
        try (AiClassifiers classifiers = create("", Map.of())) {
            assertThat(classifiers.isEnabled()).isFalse();
            assertThat(classifiers.active()).isEmpty();
        }
    }

    @Test
    void buildsTheSelectedProvider() throws Exception {
        try (AiClassifiers openAi = create("openai", Map.of("openai.api-key", "k", "openai.model", "gpt-test"));
             AiClassifiers deepSeek = create("deepseek", Map.of("deepseek.api-key", "k", "deepseek.model", "ds-test"));
             AiClassifiers anthropic = create(" anthropic ", Map.of("anthropic.api-key", "k", "anthropic.model", "claude-test"))) {
            assertThat(openAi.active().orElseThrow().provider()).isEqualTo(AiProvider.OPENAI);
            assertThat(deepSeek.active().orElseThrow().provider()).isEqualTo(AiProvider.DEEPSEEK);
            assertThat(anthropic.active().orElseThrow().provider()).isEqualTo(AiProvider.ANTHROPIC);
            assertThat(anthropic.active().orElseThrow().model()).isEqualTo("claude-test");
        }
    }

    @Test
    void theSelectedProviderNeedsAKeyAndAModel() {
        assertThatThrownBy(() -> create("anthropic", Map.of("anthropic.api-key", "k")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RELEASEFLOW_ANTHROPIC_MODEL");
        assertThatThrownBy(() -> create("openai", Map.of("openai.model", "gpt-test")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RELEASEFLOW_OPENAI_API_KEY");
    }

    @Test
    void rejectsUnknownProvidersAndCredentialsWithoutAProvider() {
        assertThatThrownBy(() -> create("gemini", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("openai, anthropic, deepseek");
        assertThatThrownBy(() -> create("", Map.of("openai.api-key", "k", "openai.model", "gpt-test")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RELEASEFLOW_AI_PROVIDER=openai");
    }

    private static AiClassifiers create(String provider, Map<String, String> settings) {
        return AiClassifierConfiguration.create(
                provider,
                TIMEOUT,
                name -> settings.getOrDefault(name, name.endsWith("base-url") ? "http://localhost:9" : ""),
                OBJECT_MAPPER
        );
    }
}
