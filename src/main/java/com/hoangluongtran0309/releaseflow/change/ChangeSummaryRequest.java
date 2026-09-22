package com.hoangluongtran0309.releaseflow.change;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A person's version of a change's neutral summary and of what it says to each
 * audience. Empty fields are allowed except what changed.
 */
public class ChangeSummaryRequest {

    static final int FIELD_LIMIT = 2000;

    @NotBlank(message = "{validation.whatChanged.required}")
    @Size(max = FIELD_LIMIT, message = "{validation.whatChanged.tooLong}")
    private String whatChanged;

    @Size(max = FIELD_LIMIT, message = "{validation.whyItChanged.tooLong}")
    private String whyChanged = "";

    @Size(max = FIELD_LIMIT, message = "{validation.technicalDetail.tooLong}")
    private String technicalDetail = "";

    @Size(max = FIELD_LIMIT, message = "{validation.migrationStep.tooLong}")
    private String migrationStep = "";

    private Map<String, String> narratives = new LinkedHashMap<>();

    public String getWhatChanged() {
        return whatChanged;
    }

    public void setWhatChanged(String whatChanged) {
        this.whatChanged = clean(whatChanged);
    }

    public String getWhyChanged() {
        return whyChanged;
    }

    public void setWhyChanged(String whyChanged) {
        this.whyChanged = clean(whyChanged);
    }

    public String getTechnicalDetail() {
        return technicalDetail;
    }

    public void setTechnicalDetail(String technicalDetail) {
        this.technicalDetail = clean(technicalDetail);
    }

    public String getMigrationStep() {
        return migrationStep;
    }

    public void setMigrationStep(String migrationStep) {
        this.migrationStep = clean(migrationStep);
    }

    public Map<String, String> getNarratives() {
        return narratives;
    }

    public void setNarratives(Map<String, String> narratives) {
        this.narratives = new LinkedHashMap<>();
        if (narratives != null) {
            narratives.forEach((code, text) -> this.narratives.put(code, clean(text)));
        }
    }

    @AssertTrue(message = "{validation.narrative.tooLong}")
    public boolean isNarrativesWithinLimit() {
        return narratives.values().stream().allMatch(text -> clean(text).length() <= FIELD_LIMIT);
    }

    NeutralSummary summary() {
        return new NeutralSummary(whatChanged, whyChanged, technicalDetail, migrationStep);
    }

    // Keeps the narratives of the given audiences that have text. Form binding fills
    // the map directly, so values are cleaned here as well.
    Map<String, String> narratives(Iterable<String> audienceCodes) {
        Map<String, String> kept = new LinkedHashMap<>();
        for (String code : audienceCodes) {
            String text = clean(narratives.get(code));
            if (!text.isEmpty()) {
                kept.put(code, text);
            }
        }
        return kept;
    }

    // Browsers submit textarea line breaks as CRLF.
    private static String clean(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").strip();
    }
}
