package com.hoangluongtran0309.releaseflow.release;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ReleaseRequest {

    @NotBlank(message = "{validation.version.required}")
    @Size(max = 50, message = "{validation.version.tooLong}")
    private String version;

    @Size(max = 2000, message = "{validation.summary.tooLong}")
    private String summary;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version == null ? null : version.strip();
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary == null || summary.isBlank() ? null : summary.strip();
    }
}
