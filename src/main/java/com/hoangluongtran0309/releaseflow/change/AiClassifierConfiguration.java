package com.hoangluongtran0309.releaseflow.change;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the one AI provider named by {@code RELEASEFLOW_AI_PROVIDER}. A selected
 * provider needs its API key and model; there is no default model. Credentials for
 * a provider that is not selected stop startup, so AI is never switched off silently.
 */
@Configuration(proxyBeanMethods = false)
class AiClassifierConfiguration {

    @Bean
    AiClassifiers aiClassifiers(
            @Value("${releaseflow.ai.provider}") String provider,
            @Value("${releaseflow.ai.timeout}") Duration timeout,
            Environment environment,
            ObjectMapper objectMapper
    ) {
        return create(provider, timeout, name -> environment.getProperty("releaseflow." + name, ""), objectMapper);
    }

    static AiClassifiers create(
            String providerValue,
            Duration timeout,
            Function<String, String> settings,
            ObjectMapper objectMapper
    ) {
        String value = providerValue == null ? "" : providerValue.strip();
        if (value.isEmpty()) {
            for (AiProvider provider : AiProvider.values()) {
                if (!setting(settings, provider, "api-key").isEmpty() || !setting(settings, provider, "model").isEmpty()) {
                    throw new IllegalStateException(
                            "RELEASEFLOW_" + provider.name() + "_API_KEY or _MODEL is set, but RELEASEFLOW_AI_PROVIDER "
                                    + "is empty. Set RELEASEFLOW_AI_PROVIDER=" + provider.getValue()
                                    + " to enable AI classification, or remove the provider settings."
                    );
                }
            }
            return new AiClassifiers(null);
        }

        AiProvider provider = AiProvider.fromValue(value).orElseThrow(() -> new IllegalStateException(
                "RELEASEFLOW_AI_PROVIDER must be one of "
                        + Arrays.stream(AiProvider.values()).map(AiProvider::getValue).collect(Collectors.joining(", "))
                        + "."
        ));
        String apiKey = setting(settings, provider, "api-key");
        String model = setting(settings, provider, "model");
        if (apiKey.isEmpty() || model.isEmpty()) {
            throw new IllegalStateException(
                    "RELEASEFLOW_AI_PROVIDER=" + provider.getValue() + " needs RELEASEFLOW_" + provider.name()
                            + "_API_KEY and RELEASEFLOW_" + provider.name() + "_MODEL."
            );
        }
        String baseUrl = setting(settings, provider, "base-url");
        return new AiClassifiers(switch (provider) {
            case OPENAI -> OpenAiCompatibleClassifier.openAi(apiKey, model, baseUrl, timeout, objectMapper);
            case DEEPSEEK -> OpenAiCompatibleClassifier.deepSeek(apiKey, model, baseUrl, timeout, objectMapper);
            case ANTHROPIC -> new AnthropicChangeClassifier(apiKey, model, baseUrl, timeout, objectMapper);
        });
    }

    private static String setting(Function<String, String> settings, AiProvider provider, String name) {
        String value = settings.apply(provider.getValue() + "." + name);
        return value == null ? "" : value.strip();
    }
}
