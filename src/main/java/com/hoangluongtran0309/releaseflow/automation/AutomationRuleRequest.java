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
