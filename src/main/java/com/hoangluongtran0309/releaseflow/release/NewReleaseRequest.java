package com.hoangluongtran0309.releaseflow.release;

import java.time.Instant;

/** A new release's details and, optionally, its planned release time. */
public class NewReleaseRequest extends ReleaseRequest {

    private String plannedReleaseAt;

    public String getPlannedReleaseAt() {
        return plannedReleaseAt;
    }

    public void setPlannedReleaseAt(String plannedReleaseAt) {
        this.plannedReleaseAt = plannedReleaseAt;
    }

    Instant plannedInstant() {
        return ReleaseScheduleRequest.parse(plannedReleaseAt);
    }
}
