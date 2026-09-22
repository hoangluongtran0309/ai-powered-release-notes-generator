# ADR-0028: A dashboard, error pages, and a browser test suite

- Status: Accepted
- Date: 2026-09-22
- Amends: [ADR-0003](0003-frontend-toolchain.md), which said "no JavaScript tests"
  because there was almost no JavaScript. That is still true, and is exactly why the
  tests added here drive a browser rather than a module.

## Context

The workspace overview said the same thing to everybody: a fixed "connect a source" card,
whether or not a source was connected. Nothing on it came from the database. A person with
several Projects had to carry `?project=` in the URL by hand, and a mistyped address
answered with raw Problem Details JSON, because `spring.mvc.problemdetails.enabled` makes
Spring MVC answer every caller that way.

None of that was visible to the test suite. Every existing test asserts on HTML as a
string, which cannot see a colour contrast, a focus order, an ARIA attribute a role
forbids, or a table that becomes unreadable at 360 px.

## Decision

- **The overview reads the workspace and offers exactly one next step.** Four figures —
  changes, needing review, breaking, releases — each linking to the page that can act on
  them, and one card: the first unfinished thing in a fixed order (no project, no source,
  no changes, changes in no release, a review to finish, a release to publish, a draft to
  continue, nothing waiting). **The order is a property of the step, not of the page**:
  `NextStep.of(...)` decides it and a plain unit test covers every branch, including the
  one that is easy to get wrong — a workspace with an open draft and no changes at all is
  still waiting for its first change, because there is nothing to put in the draft.
- **`OverviewService` owns no data.** It asks each capability for counts and composes
  them. `ChangeInboxService.counts` and `ReleaseService.counts` are aggregate queries, not
  lists that are then counted in Java.
- **The Project somebody was last looking at is remembered in a cookie, not in
  `localStorage`.** The page is server-rendered: with browser storage the first paint
  would show the wrong Project and a script would have to correct it, which is a flash of
  the wrong content on every visit. The cookie carries no authority — the Project is still
  looked up against the signed-in principal's Organization, and one that does not belong
  there is simply not among the Projects offered. It is `HttpOnly`, like the language
  cookie, because nothing in the browser reads it. Only the two list pages and the
  overview consult it; a URL that names a Project always wins.
- **An unknown address answers in the kind the caller asked for.** A browser names
  `text/html` and gets the error page; a fetch, a REST client, curl, or anything under
  `/api` keeps the Problem Details it had. Three statuses have wording of their own — 404,
  403, and everything else as a fault — because a page that guessed at a fourth would be
  telling somebody something it does not know. The address is echoed back; the exception
  never is.
- **Browser tests, and no unit tests for JavaScript.** Playwright with
  `@axe-core/playwright` against the demo Compose stack, which already brings its own
  PostgreSQL. Vitest was considered and left out: after ADR-0027 removed every string from
  `app.js`, what is left is about 150 lines of Alpine glue whose behaviour only means
  anything in a page. Three suites — the release pipeline, invitations, and a smoke pass
  over every page — run at **360 px in the light theme and 1440 px in the dark one**, and
  fail on any WCAG 2 A/AA, 2.1 A/AA or 2.2 AA violation Axe can see.
- **A dense table becomes one card per row below 640 px**, with each cell carrying its own
  heading, so nothing depends on a column header that is no longer beside it. Three pages
  have tables; the touch-target floor of 44 px (WCAG 2.5.8) is a stylesheet rule and
  applies everywhere.
- **What just happened is said in a toast that never times out.** WCAG 2.2.1 asks that
  nothing disappear on somebody who reads slowly, so a toast is dismissed by hand. It is a
  live region, announced without taking focus from the form just used, and it sits in the
  flow of the page on a phone, where an overlay would cover the thing just acted on.

## Consequences

- The accessibility gate found real defects on its first run, all of them pre-existing:
  DaisyUI's soft, ghost and outline badges miss 4.5:1 in both themes; the drawer's
  `<label>` carried an `aria-label`, which no label role permits; the release page's radio
  tabs declared `role="tablist"` around panels a tablist may not contain; and one warning
  colour and one muted code label failed against the surfaces they sit on. The fixes are
  in this change. Badge colour now lives in the background and the border, and the text
  takes the theme's own foreground.
- CI grows a fifth job. It builds the image and runs the stack, so it is the slowest gate;
  it is also the only one that can see any of the above.
- `postcss.config.js` became `postcss.config.cjs`, because the package is now an ES module
  for Playwright's sake and the PostCSS config is CommonJS.
- `ReleaseService` is public where it was package-private, for the counts the overview
  reads. Nothing else about it changed.
- A Project the cookie names but the Organization does not own is ignored rather than
  refused, so a stale cookie after leaving an Organization is a fallback, not an error.
