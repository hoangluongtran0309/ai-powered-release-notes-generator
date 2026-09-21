package com.hoangluongtran0309.releaseflow.automation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Which Notion page ids are accepted, and what they become. */
class NotionParentPageTest {

    @Test
    void readsAPageIdWrittenEitherWay() {
        String dashed = "1a2b3c4d-5e6f-4a5b-8c9d-0e1f2a3b4c5d";
        assertThat(NotionParentPage.normalized("1a2b3c4d5e6f4a5b8c9d0e1f2a3b4c5d")).isEqualTo(dashed);
        assertThat(NotionParentPage.normalized(dashed)).isEqualTo(dashed);
        assertThat(NotionParentPage.normalized("  1A2B3C4D5E6F4A5B8C9D0E1F2A3B4C5D  ")).isEqualTo(dashed);
    }

    @Test
    void refusesAMissingPage() {
        assertThatThrownBy(() -> NotionParentPage.normalized("  "))
                .isInstanceOf(AutomationActionInvalidException.class)
                .extracting(exception -> ((AutomationActionInvalidException) exception).code())
                .isEqualTo("automation_notion_parent_required");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1a2b3c4d5e6f4a5b8c9d0e1f2a3b4c5",
            "1a2b3c4d5e6f4a5b8c9d0e1f2a3b4c5dd",
            "1a2b3c4d5e6f4a5b8c9d0e1f2a3b4c5g",
            "https://www.notion.so/1a2b3c4d5e6f4a5b8c9d0e1f2a3b4c5d",
            "../../etc/passwd"
    })
    void refusesAnythingThatIsNotAPageId(String value) {
        assertThatThrownBy(() -> NotionParentPage.normalized(value))
                .isInstanceOf(AutomationActionInvalidException.class)
                .extracting(exception -> ((AutomationActionInvalidException) exception).code())
                .isEqualTo("automation_notion_parent_invalid");
    }
}
