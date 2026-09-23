package com.hoangluongtran0309.releaseflow.change;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum AiProvider {
    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic"),
    DEEPSEEK("DeepSeek");

    private final String label;

    AiProvider(String label) {
        this.label = label;
    }

    public String getValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String getLabel() {
        return label;
    }

    static Optional<AiProvider> fromValue(String value) {
        return Arrays.stream(values())
                .filter(provider -> provider.getValue().equals(value))
                .findFirst();
    }
}
