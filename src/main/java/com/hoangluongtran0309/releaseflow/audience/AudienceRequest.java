package com.hoangluongtran0309.releaseflow.audience;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The editable details of an audience. The template is checked by {@link AudienceTemplate}. */
public class AudienceRequest {

    @NotBlank(message = "{validation.displayName.required}")
    @Size(max = 120, message = "{validation.displayName.tooLong}")
    private String displayName;

    @Size(max = 1000, message = "{validation.communicationIntent.tooLong}")
    private String communicationIntent;

    private String templateBody;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? null : displayName.strip();
    }

    public String getCommunicationIntent() {
        return communicationIntent;
    }

    public void setCommunicationIntent(String communicationIntent) {
        this.communicationIntent = communicationIntent == null ? "" : communicationIntent.strip();
    }

    public String getTemplateBody() {
        return templateBody;
    }

    // Browsers submit textarea line breaks as CRLF; templates are stored with LF.
    public void setTemplateBody(String templateBody) {
        this.templateBody = templateBody == null ? null : templateBody.replace("\r\n", "\n");
    }

    String intent() {
        return communicationIntent == null ? "" : communicationIntent;
    }
}
