package com.hoangluongtran0309.releaseflow.release;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReleaseScheduleRequestTest {

    @Test
    void readsInstantsWithAnOffset() {
        assertThat(ReleaseScheduleRequest.parse("2026-10-01T09:00:00Z")).isEqualTo(Instant.parse("2026-10-01T09:00:00Z"));
        assertThat(ReleaseScheduleRequest.parse(" 2026-10-01T09:00+07:00 ")).isEqualTo(Instant.parse("2026-10-01T02:00:00Z"));
    }

    @Test
    void readsALocalDateTimeAsUtc() {
        assertThat(ReleaseScheduleRequest.parse("2026-10-01T09:00")).isEqualTo(Instant.parse("2026-10-01T09:00:00Z"));
        assertThat(ReleaseScheduleRequest.parse("2026-10-01T09:00:30")).isEqualTo(Instant.parse("2026-10-01T09:00:30Z"));
    }

    @Test
    void treatsAnEmptyValueAsNoSchedule() {
        assertThat(ReleaseScheduleRequest.parse(null)).isNull();
        assertThat(ReleaseScheduleRequest.parse("   ")).isNull();
        assertThat(new ReleaseScheduleRequest().plannedInstant()).isNull();
    }

    @Test
    void rejectsUnreadableValues() {
        for (String value : new String[]{"tomorrow", "2026-10-01", "2026-13-01T09:00", "1759309200"}) {
            assertThatThrownBy(() -> ReleaseScheduleRequest.parse(value))
                    .isInstanceOf(InvalidReleaseScheduleException.class)
                    .hasMessageStartingWith("Enter the planned release time as an ISO-8601 date and time");
        }
    }
}
