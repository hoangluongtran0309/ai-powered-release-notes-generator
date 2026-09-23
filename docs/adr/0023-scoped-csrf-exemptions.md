# ADR-0023: CSRF exemptions are scoped, never switched off

- Status: Accepted
- Date: 2026-09-20

## Context

ReleaseFlow's browser sessions are cookies, so every write a signed-in person makes
has to prove it was meant: that is what the CSRF token does, and the main filter chain
has always required one. The webhook chain, added with the first GitHub delivery, said
`csrf().disable()` instead, because a provider cannot send a token it has never seen.

CodeQL's default suite reports that as `java/spring-disabled-csrf-protection` (High).
Reading the surface shows the finding is worth acting on even though it is not
exploitable:

- The chain is genuinely cookie-free. Each delivery is proven by an HMAC over the
  request — GitHub and Linear over the raw body, GitLab over its signed headers, and
  ReleaseFlow's own automation webhook over the timestamp, delivery, **method**, **path**
  and body digest. Nothing there reads a session, and tests assert no `Set-Cookie` comes
  back. A cross-site request has no ambient authority to borrow, so CSRF is structurally
  inapplicable.

But `disable()` says more than that. It excuses everything the chain will *ever* match,
including a path somebody adds later that does use a cookie. The chain also permitted
**any** method on **any** path beneath `/webhooks/`, so a new controller mapped there
would be public the moment it existed.

## Decision

- **CSRF protection is never disabled anywhere.** Where an exemption is genuinely
  needed it is written as one: the webhook chain keeps the protection configured and
  excuses exactly the paths it serves, `csrf(csrf -> csrf.ignoringRequestMatchers("/webhooks/**"))`.
  The effect on today's requests is the same; the difference is that the exemption is
  named, so widening it later is a deliberate edit rather than a silent consequence.
- **A chain that only reads is exempt from nothing.** A future chain that answers
  strangers — a public page, say — permits the safe methods and denies the rest, which is
  exactly what a token would have refused; there is nothing left for an exemption to do.
- **The webhook chain permits only the endpoints that exist**: `POST` to
  `/webhooks/{github,gitlab,linear}/*` and `/webhooks/automation/*`, and the signed
  `GET /webhooks/automation/*/runs/*`. Everything else beneath `/webhooks/` is denied.
  A future endpoint there is refused until somebody says otherwise, which is the right
  default for the one part of the application that answers strangers.
- **The session cookie stays the second line of defence**, unchanged:
  `HttpOnly`, `SameSite=Lax` — so a cross-site POST never carries it in the first place —
  and `Secure` wherever the deployment serves HTTPS.
- **The token contract does not change.** `HttpSessionCsrfTokenRepository` with
  `X-CSRF-TOKEN` and `_csrf`, handed to REST clients by `GET /api/csrf` and written into
  every server-rendered form by the Thymeleaf dialect. Moving to a cookie-based
  repository would be a different decision with a different blast radius, and there is
  no reason for it while the only JavaScript in the application issues GETs.

## Consequences

- Nothing about how the application answers a request changes; the existing webhook,
  registration and project tests pass untouched, which is what makes this a safe
  refactor rather than a rewrite.
- `SecurityFilterChainIntegrationTest` now states the rules in one place: a session
  without a token cannot write, a signed webhook path is reached without one, and an
  unmapped path beneath `/webhooks/` is refused. A later chain adds its own case to it.
- The CodeQL alert is expected to close on the next analysis. If the query still flags
  the scoped exemption, the answer is a dismissal with this ADR as the justification —
  not a change of behaviour.
- The rule to keep: a path that proves itself by signature may be listed as an exemption;
  a path that relies on a cookie never may.
