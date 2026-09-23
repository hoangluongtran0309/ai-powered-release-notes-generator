package com.hoangluongtran0309.releaseflow.configuration;

import com.hoangluongtran0309.releaseflow.automation.ActionType;
import com.hoangluongtran0309.releaseflow.automation.ExecutionStatus;
import com.hoangluongtran0309.releaseflow.automation.TriggerType;
import com.hoangluongtran0309.releaseflow.category.CategoryGroup;
import com.hoangluongtran0309.releaseflow.change.ReviewStatus;
import com.hoangluongtran0309.releaseflow.release.ReleaseStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/** A page names these through a key built at render time, so every constant needs one. */
class EnumLabelKeyTest {

    @Test
    void everyConstantAPageNamesIsTranslated() throws IOException {
        Properties english = load("messages/ui.properties");
        Properties vietnamese = load("messages/ui_vi.properties");

        List<String> keys = List.of(
                keysOf(ReleaseStatus.values(), ReleaseStatus::getLabelKey),
                keysOf(ReviewStatus.values(), ReviewStatus::getLabelKey),
                keysOf(CategoryGroup.values(), CategoryGroup::getLabelKey),
                keysOf(ExecutionStatus.values(), ExecutionStatus::getLabelKey),
                keysOf(TriggerType.values(), TriggerType::getLabelKey),
                keysOf(ActionType.values(), ActionType::getLabelKey)
        ).stream().flatMap(joined -> java.util.Arrays.stream(joined.split(","))).toList();

        assertThat(keys).hasSizeGreaterThan(20);
        assertThat(english.stringPropertyNames()).containsAll(keys);
        assertThat(vietnamese.stringPropertyNames()).containsAll(keys);
    }

    private static <T> String keysOf(T[] values, Function<T, String> key) {
        return java.util.Arrays.stream(values).map(key).reduce((a, b) -> a + "," + b).orElseThrow();
    }

    private static Properties load(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = EnumLabelKeyTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).as(resource).isNotNull();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
