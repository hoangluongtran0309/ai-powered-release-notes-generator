package com.hoangluongtran0309.releaseflow.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ProjectRequest {

    @NotBlank(message = "Project name is required.")
    @Size(max = 120, message = "Project name must not exceed 120 characters.")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? null : name.strip();
    }
}
