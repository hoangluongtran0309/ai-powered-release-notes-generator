package com.hoangluongtran0309.releaseflow.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegistrationRequest {

    @NotBlank(message = "{validation.organizationName.required}")
    @Size(max = 120, message = "{validation.organizationName.tooLong}")
    private String organizationName;

    @NotBlank(message = "{validation.displayName.required}")
    @Size(max = 120, message = "{validation.displayName.tooLong}")
    private String displayName;

    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.format}")
    @Size(max = 254, message = "{validation.email.tooLong}")
    private String email;

    @NotBlank(message = "{validation.password.required}")
    @ValidPassword
    private String password;

    // Optional; English when omitted. Validated as a language tag by the service.
    @Size(max = 64, message = "{validation.outputLanguage.tooLong}")
    private String outputLanguage;

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.strip();
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getOutputLanguage() {
        return outputLanguage;
    }

    public void setOutputLanguage(String outputLanguage) {
        this.outputLanguage = outputLanguage == null ? null : outputLanguage.strip();
    }
}
