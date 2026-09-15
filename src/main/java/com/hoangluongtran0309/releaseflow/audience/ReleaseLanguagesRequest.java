package com.hoangluongtran0309.releaseflow.audience;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public class ReleaseLanguagesRequest {

    @NotNull(message = "List the release note languages.")
    private List<String> targetLanguages;

    public List<String> getTargetLanguages() {
        return targetLanguages;
    }

    public void setTargetLanguages(List<String> targetLanguages) {
        this.targetLanguages = targetLanguages;
    }
}
