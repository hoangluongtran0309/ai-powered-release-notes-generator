package com.hoangluongtran0309.releaseflow.automation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.util.AutoPopulatingList;

import java.util.List;
import java.util.UUID;

/**
 * A rule as a person or an API client describes it. The same object backs the REST
 * body and the page form, so both paths accept exactly the same rules.
 */
public class AutomationRuleRequest {

    @NotBlank(message = "Name is required.")
    @Size(max = 120, message = "Name must not exceed 120 characters.")
    private String name;

    @NotNull(message = "Choose what makes the rule run.")
    private TriggerType triggerType;

    private UUID projectId;

    /** The published release a schedule repeats. Only a cron rule has one. */
    private UUID releaseId;

    private String cronExpression;

    private String cronTimeZone;

    /** How many days before its planned time a release is announced, 0 through 365. */
    private Integer daysBefore;

    private List<AutomationActionRequest> actions = new AutoPopulatingList<>(AutomationActionRequest.class);

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? null : name.strip();
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(TriggerType triggerType) {
        this.triggerType = triggerType;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public UUID getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(UUID releaseId) {
        this.releaseId = releaseId;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression == null || cronExpression.isBlank() ? null : cronExpression.strip();
    }

    public String getCronTimeZone() {
        return cronTimeZone;
    }

    public void setCronTimeZone(String cronTimeZone) {
        this.cronTimeZone = cronTimeZone == null || cronTimeZone.isBlank() ? null : cronTimeZone.strip();
    }

    public Integer getDaysBefore() {
        return daysBefore;
    }

    public void setDaysBefore(Integer daysBefore) {
        this.daysBefore = daysBefore;
    }

    public List<AutomationActionRequest> getActions() {
        return actions;
    }

    public void setActions(List<AutomationActionRequest> actions) {
        this.actions = actions == null ? new AutoPopulatingList<>(AutomationActionRequest.class) : actions;
    }

    /** The rows a person actually filled in, in the order they were given. */
    List<AutomationActionRequest> describedActions() {
        return actions.stream().filter(action -> action != null && !action.isBlank()).toList();
    }
}
