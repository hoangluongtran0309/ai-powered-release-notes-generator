package com.hoangluongtran0309.releaseflow.audience;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/** A new audience. Its code is fixed once created. */
public class NewAudienceRequest extends AudienceRequest {

    @NotBlank(message = "{validation.code.required}")
    @Size(max = 64, message = "{validation.code.tooLong}")
    @Pattern(
            regexp = "[a-z][a-z0-9_]*",
            message = "{validation.code.lowercaseFormat}"
    )
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code == null ? null : code.strip().toLowerCase(Locale.ROOT);
    }
}
