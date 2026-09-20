package com.hoangluongtran0309.releaseflow.automation;

import jakarta.validation.constraints.NotBlank;

/** A schedule somebody is still writing, asked about before any rule keeps it. */
public class CronPreviewRequest {

    @NotBlank(message = "A cron expression is required.")
    private String cronExpression;

    @NotBlank(message = "A time zone is required.")
    private String cronTimeZone;

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression == null ? null : cronExpression.strip();
    }

    public String getCronTimeZone() {
        return cronTimeZone;
    }

    public void setCronTimeZone(String cronTimeZone) {
        this.cronTimeZone = cronTimeZone == null ? null : cronTimeZone.strip();
    }
}
