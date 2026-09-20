package com.hoangluongtran0309.releaseflow.automation;

import org.springframework.scheduling.support.CronExpression;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * A six-field cron expression read in one civil time zone. The zone must be a region
 * of the IANA database rather than a fixed offset, so "every Monday at 09:00" keeps
 * meaning nine in the morning on both sides of a daylight-saving change.
 */
record CronSchedule(CronExpression expression, ZoneId zone) {

    static CronSchedule of(String expression, String timeZone) {
        if (expression == null || expression.isBlank() || timeZone == null || timeZone.isBlank()) {
            throw AutomationActionInvalidException.cronInvalid();
        }
        if (!ZoneId.getAvailableZoneIds().contains(timeZone.strip())) {
            throw AutomationActionInvalidException.cronTimeZoneInvalid();
        }
        try {
            return new CronSchedule(CronExpression.parse(expression.strip()), ZoneId.of(timeZone.strip()));
        } catch (IllegalArgumentException | java.time.DateTimeException invalid) {
            throw AutomationActionInvalidException.cronInvalid();
        }
    }

    /** The first firing strictly after that moment. */
    Instant nextAfter(Instant after) {
        ZonedDateTime next = expression.next(ZonedDateTime.ofInstant(after, zone));
        if (next == null) {
            // A schedule such as 30 February never comes round again.
            throw AutomationActionInvalidException.cronHasNoFutureOccurrence();
        }
        return next.toInstant();
    }

    String expressionText() {
        return expression.toString();
    }

    String zoneText() {
        return zone.getId();
    }
}
