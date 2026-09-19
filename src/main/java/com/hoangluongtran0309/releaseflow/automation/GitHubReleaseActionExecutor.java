package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.github.GitHubApiClient;
import com.hoangluongtran0309.releaseflow.github.GitHubRelease;
import com.hoangluongtran0309.releaseflow.github.GitHubReleaseResult;
import com.hoangluongtran0309.releaseflow.project.SourceAccess;
import com.hoangluongtran0309.releaseflow.source.SourceCredentials;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Publishes the release note as the GitHub Release of the version's tag, using the
 * access token of the Project's own GitHub source rather than a secret of its own.
 * The body carries a marker naming this Action Run, so a repeat recognises the
 * release it created and a release somebody else made is never overwritten.
 */
@Component
class GitHubReleaseActionExecutor implements RuleActionExecutor {

    static final String REPOSITORY = "githubRepository";

    static final String SOURCE_MISSING = "github_source_missing";
    static final String TOKEN_MISSING = "github_token_missing";
    static final String RELEASE_CONFLICT = "github_release_conflict";
    static final String REJECTED = "github_rejected";
    static final String UNAVAILABLE = "github_unavailable";

    private final GitHubApiClient gitHubApiClient;
    private final SourceAccess sourceAccess;

    GitHubReleaseActionExecutor(GitHubApiClient gitHubApiClient, SourceAccess sourceAccess) {
        this.gitHubApiClient = gitHubApiClient;
        this.sourceAccess = sourceAccess;
    }

    @Override
    public ActionType actionType() {
        return ActionType.GITHUB_RELEASE;
    }

    @Override
    public void validate(Map<String, String> configuration, String rawSecret) {
        if (rawSecret != null && !rawSecret.isBlank()) {
            throw AutomationActionInvalidException.secretNotAllowed(ActionType.GITHUB_RELEASE);
        }
    }

    @Override
    public ActionResult execute(ActionCommand command) {
        String repository = command.configuration().get(REPOSITORY);
        Optional<SourceCredentials> credentials =
                sourceAccess.findSole(command.organizationId(), command.projectId(), SourceType.GITHUB);
        // The repository the Run was created for must still be the Project's only one.
        if (repository == null || credentials.isEmpty() || !repository.equals(repositoryOf(credentials.get()))) {
            return ActionResult.failed(SOURCE_MISSING);
        }
        Optional<String> token = credentials.get().accessToken();
        if (token.isEmpty()) {
            return ActionResult.failed(TOKEN_MISSING);
        }
        String owner = credentials.get().repositoryOwner();
        String name = credentials.get().repositoryName();
        String marker = marker(command.actionRunId());

        GitHubReleaseResult existing =
                gitHubApiClient.releaseByTag(owner, name, command.releaseVersion(), token.get());
        return switch (existing.status()) {
            case FOUND -> reconcile(existing.release(), marker);
            case ABSENT -> create(command, owner, name, token.get(), marker);
            case REJECTED -> ActionResult.failed(REJECTED);
            // Nothing was written, so reading again later is safe.
            case UNAVAILABLE, INVALID_RESPONSE, EXISTS -> ActionResult.failed(UNAVAILABLE);
        };
    }

    private ActionResult create(ActionCommand command, String owner, String name, String token, String marker) {
        GitHubReleaseResult created = gitHubApiClient.createRelease(
                owner,
                name,
                command.releaseVersion(),
                command.noteContent() + "\n\n" + marker,
                token
        );
        return switch (created.status()) {
            case FOUND -> ActionResult.succeeded(created.release().htmlUrl());
            // Someone tagged a release between the two calls; read it and see whose it is.
            case EXISTS -> settleRace(owner, name, command.releaseVersion(), token, marker);
            case REJECTED -> ActionResult.failed(REJECTED);
            // The release may exist: only a person may decide to publish it again.
            case ABSENT, UNAVAILABLE, INVALID_RESPONSE -> ActionResult.unknown(ActionResult.OUTCOME_UNKNOWN);
        };
    }

    private ActionResult settleRace(String owner, String name, String tag, String token, String marker) {
        GitHubReleaseResult existing = gitHubApiClient.releaseByTag(owner, name, tag, token);
        return existing.status() == GitHubReleaseResult.Status.FOUND
                ? reconcile(existing.release(), marker)
                : ActionResult.unknown(ActionResult.OUTCOME_UNKNOWN);
    }

    // A release this Action Run wrote is the delivery; anything else is somebody's work.
    private static ActionResult reconcile(GitHubRelease release, String marker) {
        String body = release.body() == null ? "" : release.body();
        return body.contains(marker)
                ? ActionResult.succeeded(release.htmlUrl())
                : ActionResult.failed(RELEASE_CONFLICT);
    }

    private static String repositoryOf(SourceCredentials credentials) {
        return credentials.repositoryOwner() + "/" + credentials.repositoryName();
    }

    private static String marker(UUID actionRunId) {
        return "<!-- releaseflow-action:" + actionRunId + " -->";
    }
}
