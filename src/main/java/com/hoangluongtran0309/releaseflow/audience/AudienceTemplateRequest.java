package com.hoangluongtran0309.releaseflow.audience;

/** An audience's template for one language. The template is checked by {@link AudienceTemplate}. */
public class AudienceTemplateRequest {

    private String templateBody;

    public String getTemplateBody() {
        return templateBody;
    }

    // Browsers submit textarea line breaks as CRLF; templates are stored with LF.
    public void setTemplateBody(String templateBody) {
        this.templateBody = templateBody == null ? null : templateBody.replace("\r\n", "\n");
    }
}
