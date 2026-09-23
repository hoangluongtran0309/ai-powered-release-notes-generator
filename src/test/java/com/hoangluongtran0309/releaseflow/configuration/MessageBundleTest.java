package com.hoangluongtran0309.releaseflow.configuration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the bundle itself: the two languages say the same things, every key a template
 * or a constraint asks for exists, and no pattern is written in a way MessageFormat would
 * refuse when a page fills it in.
 */
class MessageBundleTest {

    private static final Pattern MESSAGE_KEY = Pattern.compile("#\\{(?:'([A-Za-z0-9_.]+)'|([A-Za-z0-9_.]+))[(}]");
    private static final Pattern CONSTRAINT_KEY = Pattern.compile("message = \"\\{([A-Za-z0-9_.]+)}\"");

    @Test
    void bothLanguagesCarryTheSameKeys() throws IOException {
        Properties english = load("messages/ui.properties");
        Properties vietnamese = load("messages/ui_vi.properties");

        assertThat(new TreeSet<>(vietnamese.stringPropertyNames()))
                .as("every English key is translated")
                .isEqualTo(new TreeSet<>(english.stringPropertyNames()));
        assertThat(english.stringPropertyNames()).isNotEmpty();
        for (String key : english.stringPropertyNames()) {
            assertThat(english.getProperty(key)).as(key).isNotBlank();
            assertThat(vietnamese.getProperty(key)).as(key).isNotBlank();
        }
    }

    @Test
    void everyKeyATemplateAsksForIsTranslated() throws IOException {
        Set<String> keys = new TreeSet<>();
        for (Path template : templates()) {
            Matcher matcher = MESSAGE_KEY.matcher(Files.readString(template));
            while (matcher.find()) {
                String key = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                // A key built from a variable, such as #{${status.labelKey}}, is checked by
                // the enum test rather than here.
                if (!key.isBlank()) {
                    keys.add(key);
                }
            }
        }

        assertThat(keys).isNotEmpty();
        assertThat(load("messages/ui.properties").stringPropertyNames()).containsAll(keys);
        assertThat(load("messages/ui_vi.properties").stringPropertyNames()).containsAll(keys);
    }

    @Test
    void everyKeyAConstraintAsksForIsTranslated() throws IOException {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = CONSTRAINT_KEY.matcher(Files.readString(source));
                while (matcher.find()) {
                    keys.add(matcher.group(1));
                }
            }
        }

        assertThat(keys).isNotEmpty();
        assertThat(load("messages/ui.properties").stringPropertyNames()).containsAll(keys);
        assertThat(load("messages/ui_vi.properties").stringPropertyNames()).containsAll(keys);
    }

    @Test
    void everyPatternWithPlaceholdersCanBeFilledIn() throws IOException {
        for (String bundle : List.of("messages/ui.properties", "messages/ui_vi.properties")) {
            Properties properties = load(bundle);
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);
                if (!value.contains("{0}") && !value.contains("{0,")) {
                    continue;
                }
                Object[] arguments = {1, 1, 1, 1};
                assertThat(new MessageFormat(value, Locale.ENGLISH).format(arguments))
                        .as("%s in %s", key, bundle)
                        .doesNotContain("{0");
            }
        }
    }

    private static List<Path> templates() throws IOException {
        try (Stream<Path> paths = Files.walk(Path.of("src/main/resources/templates"))) {
            return paths.filter(path -> path.toString().endsWith(".html")).toList();
        }
    }

    private static Properties load(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = MessageBundleTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).as(resource).isNotNull();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
