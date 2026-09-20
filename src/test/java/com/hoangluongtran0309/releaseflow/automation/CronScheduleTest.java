package com.hoangluongtran0309.releaseflow.automation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CronScheduleTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void readsASixFieldExpressionInItsOwnTimeZone() {
        CronSchedule schedule = CronSchedule.of("0 0 9 * * *", "Europe/Berlin");

        Instant next = schedule.nextAfter(ZonedDateTime.of(2026, 3, 1, 10, 0, 0, 0, BERLIN).toInstant());

        assertThat(ZonedDateTime.ofInstant(next, BERLIN).getHour()).isEqualTo(9);
        assertThat(ZonedDateTime.ofInstant(next, BERLIN).getDayOfMonth()).isEqualTo(2);
    }

    @Test
    void keepsCivilTimeAcrossADaylightSavingChange() {
        CronSchedule schedule = CronSchedule.of("0 0 9 * * *", "Europe/Berlin");

        // Central European Summer Time begins on 29 March 2026 at 02:00 local time.
        Instant before = ZonedDateTime.of(2026, 3, 28, 12, 0, 0, 0, BERLIN).toInstant();
        Instant firstAfterTheChange = schedule.nextAfter(schedule.nextAfter(before));

        ZonedDateTime local = ZonedDateTime.ofInstant(firstAfterTheChange, BERLIN);
        assertThat(local.getDayOfMonth()).isEqualTo(30);
        assertThat(local.getHour()).isEqualTo(9);
        // The same civil hour is one hour earlier in UTC once the clocks have moved.
        assertThat(local.getOffset().getTotalSeconds()).isEqualTo(7200);
    }

    @Test
    void refusesAnythingItCannotRead() {
        assertThatThrownBy(() -> CronSchedule.of("every morning", "Europe/Berlin"))
                .isInstanceOf(AutomationActionInvalidException.class)
                .extracting(invalid -> ((AutomationActionInvalidException) invalid).code())
                .isEqualTo("automation_cron_invalid");

        // Five fields is the other projects' cron, not this one's.
        assertThatThrownBy(() -> CronSchedule.of("0 9 * * *", "Europe/Berlin"))
                .isInstanceOf(AutomationActionInvalidException.class);

        assertThatThrownBy(() -> CronSchedule.of(null, "Europe/Berlin"))
                .isInstanceOf(AutomationActionInvalidException.class);
    }

    @Test
    void demandsACivilTimeZoneRatherThanAnOffset() {
        assertThatThrownBy(() -> CronSchedule.of("0 0 9 * * *", "+01:00"))
                .isInstanceOf(AutomationActionInvalidException.class)
                .extracting(invalid -> ((AutomationActionInvalidException) invalid).code())
                .isEqualTo("automation_cron_time_zone_invalid");

        assertThatThrownBy(() -> CronSchedule.of("0 0 9 * * *", "Mars/Olympus"))
                .isInstanceOf(AutomationActionInvalidException.class);
    }

    @Test
    void refusesAScheduleThatNeverComesRoundAgain() {
        assertThatThrownBy(() -> CronSchedule.of("0 0 9 31 2 *", "Europe/Berlin").nextAfter(Instant.now()))
                .isInstanceOf(AutomationActionInvalidException.class)
                .extracting(invalid -> ((AutomationActionInvalidException) invalid).code())
                .isEqualTo("automation_cron_no_occurrence");
    }
}
