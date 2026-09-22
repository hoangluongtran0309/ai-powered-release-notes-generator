package com.hoangluongtran0309.releaseflow.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ProjectRequest {

    @NotBlank(message = "{validation.projectName.required}")
    @Size(max = 120, message = "{validation.projectName.tooLong}")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? null : name.strip();
    }
}
