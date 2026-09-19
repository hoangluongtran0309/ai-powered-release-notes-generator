# ADR-0020: Automation rules and runs

- Status: Accepted
- Date: 2026-09-19

## Context

ReleaseFlow could write a release note and freeze it, and there it stopped. Somebody
still had to copy the note into a GitHub Release, paste it into Slack, and mail it to
the people who had asked to hear about releases. Every slice up to S15 built the note;
none of them delivered it.

The old project solved this with an automation engine: a Rule holds an ordered list of
Actions, a Run records one firing of that Rule, and a worker walks the Actions one at a
time. Rebuilding it raised four questions the existing code could not answer.

- **A delivery cannot be repeated safely.** Every job ReleaseFlow had until now could be
  worked again for free: classifying a change twice yields the same change, translating
  twice yields the same text. Publishing a GitHub Release twice, posting to Slack twice,
  or mailing a thousand people twice is not free, and a worker that stops mid-call
  cannot tell whether the provider acted.
- **A rule must not be able to undo a publication.** The old project created its Runs in
  a `BEFORE_COMMIT` listener, so a Rule pointing at an audience whose note was missing
  threw, and the publication rolled back with it. A release that is ready to publish is
  not the place to discover that somebody mistyped a Slack URL.
- **A Slack webhook URL is a delivery address taken from a request**, like a GitLab
  instance or a Jira site, and the old clients followed redirects.
- **The run history is the first list in ReleaseFlow long enough to page.**

## Decision

- **An unknown outcome is a state, not a failure.** `ExecutionStatus` is
  `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `UNKNOWN`, `CANCELLED`. An action whose
  worker stopped while the provider was being called becomes `UNKNOWN` after a
  five-minute lease, never `PENDING`: it is never sent again on its own, and a person
  repeating it must confirm the duplicate. A connection that fails after a request was
  sent is `UNKNOWN` too; a provider that refused is `FAILED`. The first action that ends
  `FAILED` or `UNKNOWN` stops the ones behind it, and a retry resets only that one.
- **Publication writes an outbox row and nothing else.** `ReleaseService.publish`
  publishes a `ReleasePublished` event inside its own transaction;
  `ReleasePublishedListener` writes one `automation_publish_jobs` row and does nothing
  further — it matches no rule, reads no note, and calls nothing. `AutomationWorker`
  turns that row into runs after the commit. This is the shape ReleaseFlow already uses
  for translation jobs, and it makes "a rule cannot block a release" true by
  construction rather than by care.
- **A rule that cannot be carried out is a failed action, not a refusal.** An action
  whose audience and language have no ready note is still written, without one, and
  fails as `release_note_missing` when the worker reaches it. The operator sees a failed
  run in the history instead of a release that would not publish.
- **Enabling is the promise.** A rule is written freely and starts disabled. `enable`
  checks, in order: the project belongs to the Organization; every audience does and its
  language is one the Organization writes notes in; a GitHub Release action's project has
  exactly one GitHub source; the deployment can carry the action out at all; and the
  action's configuration and decrypted secret are usable. Disabling or archiving a rule
  cancels every run of it that had not finished.
- **`RuleActionExecutor` is an interface with three implementations**, which is what
  decision D2 allows: GitHub Release, Slack, and email all exist in this slice. The
  worker holds them in an `EnumMap` keyed by `ActionType`.
- **A GitHub Release action borrows the project's own source token.** It has no secret
  of its own; the run snapshots the repository, and the executor resolves the token
  through `SourceAccess.findSole` at delivery time. A project with no GitHub source, or
  with more than one, has no single answer, so the action fails rather than guessing.
  The release body carries `<!-- releaseflow-action:{id} -->`, so a repeat recognises the
  release it wrote and never overwrites one somebody else made.
- **A Slack webhook URL is measured against a deployment allowlist.**
  `releaseflow.automation.slack.allowed-hosts` defaults to Slack's own hosts; the URL is
  checked when the action is written and again before every delivery. Every outbound
  client this slice touches sets `HttpClient.Redirect.NEVER` explicitly, including the
  GitHub and DeepL clients that had been relying on the JDK default.
- **An action's secret is bound to the action.** It is encrypted with the existing
  `CredentialCipher`, with the Organization, rule, action, action type, and a purpose
  line as authenticated data. It is write-only: a response reports only that one is
  configured, and the run snapshots the ciphertext, not the secret.
- **A rule archives with an `active` flag** like a category, rather than the old
  project's `archived_at` and `@Version`. Neither convention exists elsewhere in this
  codebase, and an archived rule frees its name.
- **The run history pages with ReleaseFlow's own record.** `AutomationRunPage` carries
  `items`, `page`, `size`, and `total`; `page` must not be negative and `size` must be
  between 1 and 100. Spring Data's `Page` was not used, because its JSON shape is
  unstable and unlike every other response here.
- **`spring-boot-starter-mail` is the slice's one new dependency**, and email is the one
  action with no credentials of its own: it uses the deployment's SMTP server, so a rule
  that needs it cannot be enabled until `spring.mail.host` and
  `releaseflow.automation.email.from` are set.

## Consequences

- The worker ticks every second and drains what it finds, one action at a time, claiming
  with `FOR UPDATE OF action_run SKIP LOCKED` and only once every earlier action of the
  run has succeeded. Several instances are safe without a leader.
- An action run's position is unique per run, and a rule action's position is unique per
  rule and `DEFERRABLE INITIALLY DEFERRED`, so reordering a rule's actions inside one
  transaction never collides with itself.
- A publication fires each rule once — `automation_runs (rule_id, release_id)` is unique
  for `RELEASE_PUBLISHED` — and a repeated manual request returns the same run, because
  `(rule_id, request_id)` is unique too.
- An action run records an error code, never a message from a provider or an exception,
  in keeping with the rule that failures store fixed, safe text.
- Only `RELEASE_PUBLISHED` and `MANUAL` exist. Cron schedules, upcoming-release
  reminders, and signed incoming webhooks are the next slice, and they add trigger kinds
  to the same tables.
