package com.hoangluongtran0309.releaseflow.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class OutputLanguageRequest {

    @NotBlank(message = "An output language is required.")
    @Size(max = 64, message = "The output language is too long.")
    private String outputLanguage;

    public String getOutputLanguage() {
        return outputLanguage;
    }

    public void setOutputLanguage(String outputLanguage) {
        this.outputLanguage = outputLanguage == null ? null : outputLanguage.strip();
    }
}
