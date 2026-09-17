package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.jira.LinkedIssue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

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
            "https://github.com/acme/app/pull/12",
            List.of()
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
    void listsTheIssuesAChangeMentionsAndLeavesTheSectionOutWithoutThem() {
        String template = "- {{whatChanged}}{{#linkedIssues}} [{{key}}]({{url}}) {{title}} ({{type}}, {{status}}){{/linkedIssues}}";
        AudienceItem linked = new AudienceItem("Export.", "", "", "", "", 12, "https://github.com/acme/app/pull/12",
                List.of(
                        new LinkedIssue("APP-7", "Export tables", "never shown", "Story", "Done",
                                "https://acme.atlassian.net/browse/APP-7"),
                        new LinkedIssue("APP-8", "Stream rows", "", "Task", "Done",
                                "https://acme.atlassian.net/browse/APP-8")
                ));

        AudienceTemplate.validate(template);
        assertThat(AudienceTemplate.render(template, linked)).isEqualTo(
                "- Export. [APP-7](https://acme.atlassian.net/browse/APP-7) Export tables (Story, Done)"
                        + " [APP-8](https://acme.atlassian.net/browse/APP-8) Stream rows (Task, Done)");
        assertThat(AudienceTemplate.render(template, ITEM)).isEqualTo("- Adds <b>CSV</b> & JSON export.");
        assertThat(AudienceTemplate.sample(template)).contains("[APP-7](https://acme.atlassian.net/browse/APP-7)");
        assertThat(AudienceItem.VARIABLES).endsWith("linkedIssues");
        // A tracker's description is long, untrusted prose, so a note cannot print it.
        assertThatThrownBy(() -> AudienceTemplate.validate("{{#linkedIssues}}{{description}}{{/linkedIssues}}"))
                .isInstanceOf(InvalidAudienceTemplateException.class);
    }

    @Test
    void theShippedPresetsIgnoreLinkedIssues() {
        AudienceItem linked = new AudienceItem(ITEM.whatChanged(), ITEM.whyChanged(), ITEM.technicalDetail(),
                ITEM.migrationStep(), ITEM.narrative(), ITEM.pullRequestNumber(), ITEM.pullRequestUrl(),
                List.of(new LinkedIssue("APP-7", "Export tables", "", "Story", "Done",
                        "https://acme.atlassian.net/browse/APP-7")));

        AudiencePresets.forLanguage("en").forEach(preset -> assertThat(AudienceTemplate.render(preset.templateBody(), linked))
                .isEqualTo(AudienceTemplate.render(preset.templateBody(), ITEM)));
    }

    @Test
    void acceptsEveryShippedPreset() {
        for (String language : new String[]{"en", "vi", "pt-BR"}) {
            AudiencePresets.forLanguage(language).forEach(preset -> AudienceTemplate.validate(preset.templateBody()));
        }
    }
}
