package com.hoangluongtran0309.releaseflow.jira;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class JiraAdfTextTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void turnsBlocksIntoLinesAndFlattensMarks() {
        JsonNode adf = JSON.readTree("""
                {"type":"doc","version":1,"content":[
                  {"type":"heading","attrs":{"level":2},"content":[{"type":"text","text":"Export"}]},
                  {"type":"paragraph","content":[
                    {"type":"text","text":"Streams "},
                    {"type":"text","text":"large","marks":[{"type":"strong"}]},
                    {"type":"text","text":" tables."}
                  ]},
                  {"type":"bulletList","content":[
                    {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"CSV"}]}]},
                    {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"JSON"}]}]}
                  ]}
                ]}""");

        assertThat(JiraAdfText.convert(adf, 8000)).isEqualTo("Export\nStreams large tables.\nCSV\n\nJSON");
    }

    @Test
    void collapsesSpacesAndBlankLines() {
        JsonNode adf = JSON.readTree("""
                {"type":"doc","content":[
                  {"type":"paragraph","content":[{"type":"text","text":"One  \\t two"}]},
                  {"type":"paragraph","content":[]},
                  {"type":"paragraph","content":[]},
                  {"type":"paragraph","content":[{"type":"text","text":"three"}]}
                ]}""");

        assertThat(JiraAdfText.convert(adf, 8000)).isEqualTo("One two\n\nthree");
    }

    @Test
    void stopsAtTheCapWhileWalking() {
        JsonNode adf = JSON.readTree("""
                {"type":"doc","content":[
                  {"type":"paragraph","content":[{"type":"text","text":"abcdefghij"}]},
                  {"type":"paragraph","content":[{"type":"text","text":"never read"}]}
                ]}""");

        assertThat(JiraAdfText.convert(adf, 4)).isEqualTo("abcd");
    }

    @Test
    void acceptsAPlainStringAndNothingAtAll() {
        assertThat(JiraAdfText.convert(JSON.readTree("\"  plain text  \""), 8000)).isEqualTo("plain text");
        assertThat(JiraAdfText.convert(JSON.missingNode(), 8000)).isEmpty();
        assertThat(JiraAdfText.convert(null, 8000)).isEmpty();
    }
}
