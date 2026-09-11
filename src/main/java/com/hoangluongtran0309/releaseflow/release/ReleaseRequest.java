package com.hoangluongtran0309.releaseflow.release;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ReleaseRequest {

    @NotBlank(message = "Version is required.")
    @Size(max = 50, message = "Version must not exceed 50 characters.")
    private String version;

    @Size(max = 2000, message = "Summary must not exceed 2000 characters.")
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
