# ADR-0021: Scheduled and signed automation triggers

- Status: Accepted
- Date: 2026-09-20

## Context

ADR-0020 gave a Rule two ways to fire: a release was published, or a person ran the
Rule by hand. Both are answers to something that has just happened, and both arrive with
a request behind them. The three triggers left over in the old project are not like
that.

- **A schedule** repeats a release that went out days ago, for the people who were not
  watching when it did. Nobody asks for it, so nothing carries a tenant, and the work is
  found rather than received. A deployment that was down over the weekend must not owe
  three days of catch-up deliveries.
- **A reminder** is due because an approved release is getting close, which is a fact
  about a release rather than an event. Moving the release moves the reminder.
- **A call from another system** is a request, but an unauthenticated one: there is no
  session, so whatever proves it must also say whose Organization it belongs to. The
  body cannot, because a body is whatever the caller wrote.

The ways ReleaseFlow already had of answering these questions did not fit. A poll's
schedule lives on the row being polled (`integration_sources.next_poll_at`), but a poll
is idempotent and an automation delivery is not. The provider webhooks are verified with
a secret the source owns, but each of them verifies what that provider happens to send;
there is no signature ReleaseFlow itself defines.

## Decision

- **Trigger configuration lives on `automation_rules` as columns, not JSON.** The
  schedule, its zone, the release it repeats, the reminder's notice, and the webhook's
  identity are all things the database must index, lock, or check. `V23` adds them, with
  a CHECK per trigger that makes a rule carry exactly what its trigger needs and nothing
  else. Changing a rule's trigger clears the rest.
- **A schedule is six cron fields in an IANA time zone.** A fixed offset is refused, so
  "every Monday at 09:00" stays nine in the morning on both sides of a daylight-saving
  change. The expression is parsed before it is ever stored, and again when the rule is
  enabled; a schedule with no future occurrence is not a schedule.
- **The next firing is always computed from the present.** `AutomationTriggerWorker`
  claims a due rule with `FOR UPDATE SKIP LOCKED`, writes at most one Run for the
  occurrence it found, and then books the next firing from *now* rather than from that
  occurrence. A week of downtime therefore costs one catch-up Run, not a week of them.
  Disabling or archiving a rule clears `next_fire_at`, which a CHECK also requires, so a
  rule that is off can never be claimed.
- **A schedule binds to one published release, and takes that release's project.** The
  project is not a separate choice, so the foreign key ties rule, release, and project
  together and no rule can repeat a release outside the project it watches. A release
  that is no longer published makes the schedule skip that occurrence and keep its place;
  it is not an error and it is not a delivery.
- **A reminder's identity is the moment it answers.** `scheduled_for` is the release's
  planned time less the rule's notice, and the run is unique per rule, release, and that
  moment. Rescheduling a release therefore earns exactly one further reminder, and
  scanning the same state again earns none. With no notice at all, the scan looks a
  configurable tick ahead (`PT15S`) rather than at one exact instant, because otherwise
  the moment would have to be hit exactly.
- **A scheduled rule may only tell people something.** Cron and reminder rules accept
  Slack and email actions alone. Nobody is watching when a schedule goes off, and
  publishing a GitHub Release from one would be a change to the outside world that no
  person asked for at that moment.
- **The signature is the identity of a call from outside.** HMAC-SHA256 over the
  timestamp, the delivery UUID, the uppercase method, the request path, and the hex
  SHA-256 of the raw body, each on its own line, keyed by a 256-bit secret the rule owns.
  Covering the method and the path means a signature for one call cannot be replayed
  against another. The comparison is constant-time, the allowed clock skew is five
  minutes, and the answer is the same 401 whether the path is unknown, the rule is
  disabled, the moment is stale, or the signature is simply wrong.
- **The Organization comes from the rule; the body names only a release.** A body
  larger than 64 KiB is refused before the rule is looked up — an automation call carries
  one identifier, so it is held far below the megabyte a provider's own delivery may
  need. The delivery UUID is stored as the run's `request_id`, which the existing
  `automation_runs_request_once` index already makes unique per rule, so a caller that
  retries after a timeout gets its first run back rather than a second delivery.
- **The webhook secret is bound to the rule and to its path.** `AutomationSecrets`
  encrypts it with its own purpose line, so it can never be read as an action's secret.
  It is shown once, when the rule is created and whenever it is rotated; rotation keeps
  the path and stops the old secret at once, because a secret that two callers could use
  is not a secret ReleaseFlow can reason about.
- **The trigger worker only writes Runs.** It calls nothing outside; `AutomationWorker`
  remains the one place a provider is reached, with the claim and result transactions of
  ADR-0020 unchanged. A schedule and a call from outside therefore inherit the whole of
  that decision: ordered actions, an unknown outcome that waits for a person, and a
  cancellation that lets the action already running record its result.

## Consequences

- A deployment now runs two automation workers: one that finds work (every 15 seconds by
  default) and one that does it (every second). Both can be turned off separately, and
  both are off in tests, which drive `processOne()` by hand.
- `automation_runs.scheduled_for` divides runs into those somebody asked for and those
  ReleaseFlow found. The two partial unique indexes over it are what make a repeated scan
  free.
- `/webhooks/automation/{webhookId}` is the first endpoint ReleaseFlow both defines the
  signature of and answers; the provider webhooks each follow their provider's scheme.
  Its refusals reuse `WebhookSignatureInvalidException` and
  `WebhookPayloadTooLargeException`, which already mean 401 and 413 for every other
  webhook.
- Reminders read approved releases through one new method on `ReleaseAccess`, keeping the
  release package the only place a release is read from.
- A rule whose schedule can no longer be parsed — a time zone dropped from the JDK, say —
  stops rather than failing every tick: the worker clears its booking and logs it, and
  enabling the rule again is what gives it a new one.
