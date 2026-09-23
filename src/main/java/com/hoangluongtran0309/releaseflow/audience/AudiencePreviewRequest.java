package com.hoangluongtran0309.releaseflow.audience;

public class AudiencePreviewRequest {

    private String templateBody;

    public String getTemplateBody() {
        return templateBody;
    }

    public void setTemplateBody(String templateBody) {
        this.templateBody = templateBody == null ? null : templateBody.replace("\r\n", "\n");
    }
}
