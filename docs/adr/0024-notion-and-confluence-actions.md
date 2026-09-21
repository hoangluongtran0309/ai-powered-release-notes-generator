# ADR-0024: Notion and Confluence actions

- Status: Accepted
- Date: 2026-09-21

## Context

[ADR-0020](0020-automation-rules-and-runs.md) settled what an Action is: an ordered step
of a rule, with its own configuration, its own secret, and exactly one executor that
knows how to carry it out. [ADR-0022](0022-public-changelog.md) added a fourth kind
without changing any of that.

Teams that keep their documentation in a wiki want the release note to arrive there as a
page, not as a message that scrolls away. Notion and Confluence Cloud are the two such
destinations the old project supported, and they are the last two Actions of the
automation milestone.

Both write something that stays, which makes them unlike Slack and email in the way that
matters most: a delivery nobody could confirm cannot simply be tried again.

## Decision

- **Two more `ActionType` values, and nothing else new.** `NOTION` and `CONFLUENCE` are
  ordinary Actions: the same run, ordering, claim, retry, and cancellation as every
  other. `V25` widens the two `action_type` CHECK constraints and adds no table and no
  column. What each one needs is written into the `configuration` object an Action
  already carries, and its credential into the secret columns already there — bound, as
  ADR-0020 decided, to the Action's own id *and its type*, so a Notion token can never be
  read back as a Confluence one.
- **Notion is a host the deployment names; Confluence is an address the Action carries.**
  That difference decides how each is guarded. Notion has one API, so
  `releaseflow.automation.notion.api-base-url` is deployment configuration and no request
  ever chooses where a page is written. A Confluence site is chosen per Action, so
  `ConfluenceSite` requires exactly one label beneath `atlassian.net` over HTTPS with no
  credentials, port, path, query, or fragment — and checks it again before every
  delivery, so a row that changed in the database cannot redirect a release note. Neither
  client follows a redirect, and a `3xx` is recorded as a refusal rather than a delivery:
  nothing was written wherever it pointed.
  `releaseflow.automation.confluence.api-base-url` exists only so a local stand-in can
  answer; the site is still validated, and every link ReleaseFlow records is built from
  the site itself, never from the stand-in.
- **The Notion API version is pinned by configuration, next to the body it belongs to.**
  Notion versions its API by date and the shape of a page depends on which version is
  asked. `releaseflow.automation.notion.version` pins one and the executor sends the
  Markdown the pinned version accepts, so moving to another version is a deployment
  change made together with a code change, never a silent drift.
- **Confluence is sent the HTML the application already trusts.** `MarkdownHtml.render`
  escapes raw HTML, sanitizes link targets, and turns images into plain links; that
  output is the `storage` body. A release note is written by people and may quote
  anything, so nothing hand-rolls an escape here.
- **A size limit is a local decision, taken before the network.** 450,000 bytes for
  Notion and 2,000,000 for Confluence, measured on what would actually be sent. A note
  too large fails as itself rather than as whatever the provider says about a request it
  refused to read.
- **4xx is `FAILED`, 5xx and a broken connection are `UNKNOWN`.** A refusal is a decision
  the provider made and repeating it changes nothing. Anything else may have created the
  page before the answer was lost, so it becomes the state only a person may clear —
  ADR-0020's confirmation of a possible duplicate.
- **The page carries a marker, and nothing reads it back.**
  `<!-- releaseflow-action:{actionRunId} -->` says which run wrote a page, which is worth
  having when somebody is deciding whether a repeat is safe. It is not idempotency: the
  GitHub Release Action can reconcile because a tag is unique, while Confluence will
  happily hold two pages with one title, and finding a previous page would mean a search
  whose own failure modes are new. The honest answer is the `UNKNOWN` state, not a guess.
- **Neither may be fired by a schedule.** The existing rule stands unchanged: a rule
  ReleaseFlow sets off itself may only tell people something. A cron rule would file the
  same page again every cycle, and a reminder works on a release that is not published.

## Consequences

- Both Actions can leave a page behind that ReleaseFlow will never clean up or find
  again. That is the cost of not searching before writing, and it is why a retry after an
  unknown outcome still asks a person to confirm the duplicate.
- The Notion body is tied to one pinned API version. A deployment that moves to another
  version and keeps the old body will see `FAILED` deliveries with a stable error code
  rather than half-written pages.
- Confluence Cloud only. Data Center and OAuth have a different address shape and a
  different credential lifecycle, and neither is in this slice.
- A fifth and sixth way of holding provider configuration did not appear: both fit in the
  Action's existing configuration object, which is now carrying four keys for Confluence.
  A seventh destination with more than that is the point at which a typed, per-type
  configuration would earn its place.
