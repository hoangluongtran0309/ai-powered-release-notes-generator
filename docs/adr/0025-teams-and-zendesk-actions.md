# ADR-0025: Microsoft Teams and Zendesk actions

- Status: Accepted
- Date: 2026-09-21
- Amends: [ADR-0020](0020-automation-rules-and-runs.md) and
  [ADR-0022](0022-public-changelog.md), which name Slack and email as the only actions a
  rule ReleaseFlow sets off itself may take. Microsoft Teams now joins them, for the
  reason those documents give rather than despite it.

## Context

[ADR-0024](0024-notion-and-confluence-actions.md) added the two wiki destinations. These
are the last two of the old project's eight: a message through a Microsoft Teams Workflows
callback, and an article in a Zendesk Help Center.

They are opposites, and almost every decision below follows from that. Teams *tells people
something*: the message scrolls away and nothing is left to be found again. Zendesk
*publishes a page*: an article that exists cannot be unsaid, and a second one is somebody's
problem.

## Decision

- **Teams may be fired by a schedule; Zendesk may not.** The rule ReleaseFlow has always
  kept is that a rule nobody is watching may only tell people something. Until now that
  was written as "Slack and email", because those were the only two that qualified. Teams
  qualifies for exactly the same reason, so the check is now `tellsPeople(actionType)` and
  the rule says what it always meant. Zendesk joins GitHub Releases, Notion, Confluence and
  the public changelog on the other side: a cron rule would publish the same article every
  cycle, and a reminder works on a release that is not published yet.
- **The Teams callback is guarded by its shape, not by an allowlist.** The URL a Workflows
  flow hands out is both the destination and the credential — the `sig` in its query is
  what authorises the call. Slack's deployment allowlist cannot be copied here, because the
  host label belongs to the customer's own environment and no list could name it in
  advance. So `TeamsWebhookUrl` requires HTTPS, one label beneath
  `.environment.api.powerplatform.com`, the default port or 443, no credentials or
  fragment, the Workflows trigger path (with or without the newer `cu/{unit}/` segment),
  and a `sig` parameter with a value. It is checked when the action is written and again
  before every delivery. Because the URL is a credential, a redirect is never followed:
  following one would hand the signature to another origin.
- **The query is split and walked, not matched.** A pattern asked to find `sig=` inside a
  string somebody else chose is the shape of problem the ReDoS fixes in this repository
  already removed once. Splitting on `&` and comparing is plainer and cannot back-track.
- **Teams is sent plain text, and the request is what gets measured.** One JSON object with
  a `text` field holding `Release <version>`, a newline, and the note. Teams bounds the
  request rather than the message, so the payload is written first and its 28 KiB is
  measured on those bytes — the check still happens before any network call.
- **A Zendesk delivery is two calls, and they are allowed to fail differently.** A
  short-lived token from `POST /oauth/tokens` with the client-credentials grant and `write`
  scope, then the article. Nothing is published until the second call, so **every** way the
  first can fail is `FAILED`, including a server error and a lost connection: repeating it
  cannot duplicate an article that was never created, and calling it `UNKNOWN` would ask a
  person to confirm a duplicate that cannot exist. The second call follows the usual rule —
  4xx including 429 is `FAILED`, 5xx or a lost connection is `UNKNOWN`. A 2xx that names no
  article is `UNKNOWN` too, because nobody can say from it whether one was written.
- **The token lives for one delivery.** It is a local variable, fetched per delivery, never
  cached, stored, or logged. Caching it would mean holding somebody's credential across
  deliveries for a saving nothing here needs.
- **A Zendesk subdomain is one DNS label, not a URL.** `ZendeskTenant` lower-cases it and
  accepts only a label, so the only address the action can reach is
  `https://{subdomain}.zendesk.com` — there is nothing in the configuration for a request
  to point elsewhere. Section and user-segment ids are positive numbers this side can still
  count with.
- **The article is the HTML the application already trusts.** `MarkdownHtml.render`, as the
  public changelog and the Confluence action use, bounded at 1,000,000 bytes before the
  token is even asked for. The article is created with `draft=false` and
  `notify_subscribers=false`, and carries `user_segment_id` only when one is configured:
  ReleaseFlow does not decide to email a customer's subscribers.
- **Two more stand-in base URLs, for the same reason as the last slice.**
  `releaseflow.automation.teams.api-base-url` replaces only the origin and keeps the path
  and the signature; `releaseflow.automation.zendesk.api-base-url` replaces the help
  centre's origin. Both are empty by default, both leave the validation in place, and
  neither changes what is recorded: a Zendesk link is the `html_url` Zendesk itself
  returned.

## Consequences

- A rule fired by a schedule can now reach a chat, which is what most people wanted from
  one. The invariant is looser in letter and identical in spirit; the conflict message,
  `AGENTS.md`, and the README all say the new list.
- A Zendesk OAuth failure reads as `FAILED` even when Zendesk was the thing that broke.
  That is deliberate and is the one place in automation where a 5xx does not become
  `UNKNOWN`; `zendesk_auth_unavailable` distinguishes it from a refusal for anybody
  reading run history.
- An article, like a Notion or Confluence page, cannot be found again: nothing searches
  for one a previous run wrote. An unconfirmed delivery stays `UNKNOWN` until a person
  accepts that a second article may appear.
- Eight kinds of Action now share one `configuration` object and one pair of secret
  columns. That has held without strain, but Zendesk's four keys are the most any action
  carries; a ninth destination needing more is where a typed, per-type configuration would
  finally earn its place.
- Only Teams Workflows in the commercial cloud. Office 365 connectors are being retired,
  and sovereign clouds, Graph bots, and Entra-authenticated flows have different addresses
  and a different credential lifecycle.
