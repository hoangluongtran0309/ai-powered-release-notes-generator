package com.hoangluongtran0309.releaseflow.release;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * The planned release time, or none. REST clients send an ISO-8601 instant with an
 * offset; the page's {@code datetime-local} input sends a local time, read as UTC.
 */
public class ReleaseScheduleRequest {

    private String plannedReleaseAt;

    public String getPlannedReleaseAt() {
        return plannedReleaseAt;
    }

    public void setPlannedReleaseAt(String plannedReleaseAt) {
        this.plannedReleaseAt = plannedReleaseAt;
    }

    Instant plannedInstant() {
        return parse(plannedReleaseAt);
    }

    static Instant parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.strip();
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
            // Fall through to a local date-time without an offset.
        }
        try {
            return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException exception) {
            throw new InvalidReleaseScheduleException(InvalidReleaseScheduleException.UNREADABLE);
        }
    }
}
