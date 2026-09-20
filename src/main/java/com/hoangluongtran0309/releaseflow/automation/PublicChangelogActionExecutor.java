package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.changelog.Publication;
import com.hoangluongtran0309.releaseflow.changelog.PublicChangelogService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes the note to the Organization's own public changelog. Alone among the
 * Actions it reaches no provider: the delivery is a row in ReleaseFlow's database, so
 * it opens its own short transaction — the worker calls an executor with none open.
 *
 * <p>Because the destination is ours, a repeat is safe in a way no other delivery is:
 * the same note published twice is the entry that is already there. Different words for
 * a release that is already public are refused rather than written over, since somebody
 * may have read the first version.
 */
@Component
class PublicChangelogActionExecutor implements RuleActionExecutor {

    static final String CONFLICT = "public_changelog_conflict";

    private final PublicChangelogService changelog;

    PublicChangelogActionExecutor(PublicChangelogService changelog) {
        this.changelog = changelog;
    }

    @Override
    public ActionType actionType() {
        return ActionType.PUBLIC_CHANGELOG;
    }

    @Override
    public void validate(Map<String, String> configuration, String rawSecret) {
        if (rawSecret != null && !rawSecret.isBlank()) {
            throw AutomationActionInvalidException.secretNotAllowed(ActionType.PUBLIC_CHANGELOG);
        }
    }

    @Override
    public ActionResult execute(ActionCommand command) {
        Publication publication = changelog.publish(new Publication.Request(
                command.actionRunId(),
                command.organizationId(),
                command.projectId(),
                command.releaseId(),
                command.releaseVersion(),
                command.audienceName(),
                command.language(),
                command.noteContent()
        ));
        return publication.outcome() == Publication.Outcome.PUBLISHED
                ? ActionResult.succeeded(publication.url())
                : ActionResult.failed(CONFLICT);
    }
}
