package com.hoangluongtran0309.releaseflow.automation;

import java.util.Map;

/**
 * One kind of delivery. Each Action type has exactly one executor: it says what a
 * valid configuration looks like, whether the deployment can carry it out at all,
 * and how to perform it. Implementations call the network and therefore run outside
 * every database transaction.
 */
interface RuleActionExecutor {

    ActionType actionType();

    /** Rejects a configuration or secret no delivery of this kind could use. */
    void validate(Map<String, String> configuration, String rawSecret);

    /** Refuses enabling a Rule this deployment could never carry out. */
    default void validateAvailability() {
    }

    ActionResult execute(ActionCommand command);
}
