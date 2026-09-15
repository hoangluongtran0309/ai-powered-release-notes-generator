package com.hoangluongtran0309.releaseflow.audience;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudienceTemplateTest {

    private static final AudienceItem ITEM = new AudienceItem(
            "Adds <b>CSV</b> & JSON export.",
            "",
            "Streams rows.",
            "",
            "Download data from any table.",
            12,
            "https://github.com/acme/app/pull/12"
    );

    @Test
    void rendersEveryVariableAndLeavesOutEmptySections() {
        String template = """
                - {{whatChanged}} [#{{pullRequestNumber}}]({{pullRequestUrl}})
                {{#whyChanged}}
                  - Why: {{.}}
                {{/whyChanged}}
                {{#technicalDetail}}
                  - Detail: {{.}}
                {{/technicalDetail}}
                {{#narrative}}
                  - {{.}}
                {{/narrative}}
                {{migrationStep}}""";

        assertThat(AudienceTemplate.render(template, ITEM)).isEqualTo("""
                - Adds <b>CSV</b> & JSON export. [#12](https://github.com/acme/app/pull/12)
                  - Detail: Streams rows.
                  - Download data from any table.
                """);
    }

    @Test
    void rendersTheSampleForThePreview() {
        assertThat(AudienceTemplate.sample("{{whatChanged}} — {{narrative}}"))
                .isEqualTo("A safer deployment workflow is now available. — "
                        + "Release managers can review audience-specific notes before they are published.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n"})
    void rejectsABlankTemplate(String body) {
        assertThatThrownBy(() -> AudienceTemplate.validate(body))
                .isInstanceOf(InvalidAudienceTemplateException.class)
                .hasMessage("A template is required.")
                .extracting("code").isEqualTo(InvalidAudienceTemplateException.INVALID);
    }

    @Test
    void rejectsAnOverlongTemplate() {
        assertThatThrownBy(() -> AudienceTemplate.validate("x".repeat(AudienceTemplate.MAX_LENGTH + 1)))
                .isInstanceOf(InvalidAudienceTemplateException.class)
                .hasMessage("A template must not exceed 10000 characters.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{{narratives.operator}}", "{{ narratives . end_user }}", "{{&narratives.x}}", "{{{narratives.x}}}"})
    void rejectsATemplateThatNamesAnotherAudience(String body) {
        assertThatThrownBy(() -> AudienceTemplate.validate(body))
                .isInstanceOf(InvalidAudienceTemplateException.class)
                .extracting("code").isEqualTo(InvalidAudienceTemplateException.NARRATIVES_PATH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{{#whatChanged}}unclosed", "{{userImpact}}", "{{#unknown}}x{{/unknown}}", "{{/whatChanged}}"})
    void rejectsInvalidMustacheAndUnknownVariables(String body) {
        assertThatThrownBy(() -> AudienceTemplate.validate(body))
                .isInstanceOf(InvalidAudienceTemplateException.class)
                .hasMessageStartingWith("This template is not valid Mustache: ")
                .extracting("code").isEqualTo(InvalidAudienceTemplateException.INVALID);
    }

    @Test
    void acceptsEveryShippedPreset() {
        for (String language : new String[]{"en", "vi", "pt-BR"}) {
            AudiencePresets.forLanguage(language).forEach(preset -> AudienceTemplate.validate(preset.templateBody()));
        }
    }
}
