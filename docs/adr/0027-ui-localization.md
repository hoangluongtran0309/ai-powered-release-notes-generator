# ADR-0027: UI localization

- Status: Accepted
- Date: 2026-09-22

## Context

Everything ReleaseFlow said to a person was English: page copy, form labels, validation
messages, and the `detail` of every Problem Details response. The product already knew
about languages — an Organization picks the language its AI summaries and release notes
are written in, and a release note can exist in several — but the interface around that
content had only one.

Those are two different languages, and conflating them would be a mistake. The Organization's
output language belongs to the content and is a snapshot: a published note keeps the language
it was published in forever. The interface language belongs to one person reading one page
right now, and changing it must not touch a single stored word.

## Decision

- **Two axes, never mixed.** `RELEASEFLOW_UI_LANGUAGES` (default `en,vi`) lists the
  languages this deployment ships the interface in. It is unrelated to
  `RELEASEFLOW_OUTPUT_LANGUAGES`, to `organizations.output_language`, and to a release's
  target languages. A page's chrome follows the reader; the release note inside it keeps the
  language it was written in.
- **One order, four steps, each narrowed to a language this deployment ships.** The
  `releaseflow_lang` cookie, then `app_users.ui_locale`, then `Accept-Language`, then the
  first configured language. A step that names a language ReleaseFlow does not ship falls
  through to the next rather than to a missing bundle, and a region is dropped first, so
  `vi-VN` and `vi` ask for the same thing.
- **The account's choice is read from the session's principal, never from a query.**
  Resolving a locale happens on every request, including static resources and webhooks, so
  it must cost nothing. `ReleaseFlowPrincipal` carries a snapshot of the column taken when
  the session began. A change writes the cookie as well, and the cookie outranks the
  account, so the person never waits for a new session to see their language.
- **The cookie is HttpOnly.** Nothing in the browser reads it: every label is rendered on
  the server, and the three Alpine components that used to hold English strings now read
  them from `data-` attributes the template filled in. This departs from the older design,
  which embedded the bundle as JSON so scripts could call `t()`; with server-rendered forms
  there is nothing for such a runtime to do.
- **A failure carries a bundle key, not a sentence.** Every user-facing exception extends
  `LocalizedException`, whose message is a key and which carries the values the sentence
  fills in. `ApiExceptionHandler` resolves the title from `error.<code>.title` and the
  detail from the key the failure itself carried. **The `code` is what a caller matches on
  and does not change**; only `title` and `detail` are translated. Page controllers resolve
  the same failure through `UiMessages`, so a form and an API answer the same wording.
- **Bean Validation reads the same bundle.** Every constraint names a key
  (`message = "{validation.email.format}"`), and a validator wired to a message source over
  `messages/ui` resolves it in the language of the request. That message source deliberately
  does not use the code as a default, so a constraint ReleaseFlow has not given a message
  still falls back to Bean Validation's own wording rather than printing a key.
- **A missing translation shows the key.** `spring.messages.use-code-as-default-message` is
  on and `fallback-to-system-locale` is off, so a gap is visible on the page and in tests
  instead of hiding behind whichever language happens to be complete. An integration test
  renders every page in Vietnamese and fails on any `ui.`, `error.` or `validation.` key
  that reaches the HTML.
- **Public changelog pages localize their chrome only.** They resolve through the same
  cookie and `Accept-Language`, reach neither the account step nor a session, and leave the
  published note in the language it was published in. Those pages are cached publicly, so
  they now declare `Vary: Accept-Language, Cookie`: the note is the same for everybody, but
  the chrome is not, and a shared cache must not hand one reader's language to another.
- **The RSS feed stays English.** A feed is one shared, cached document, and the
  `Accept-Language` of a feed reader says nothing about who subscribed. Its two sentences —
  the channel title and description — are therefore not translated, and the entries inside
  it were never interface text.
- **Recorded evidence stays as it was recorded.** A stored AI failure, a classification
  reason, a review trigger's detail and a provider's own name are data, not interface. The
  sentence around a stored AI failure is translated and the failure itself is passed into it
  as a value. A review trigger now answers with a key and its detail rather than a finished
  English sentence, because nothing stores that sentence.
- **`app_users.ui_locale` is nullable, and null means the browser decides.** Every account
  that existed before this migration keeps that behavior without being touched.

## Consequences

- Adding a language is a bundle file plus one entry in `RELEASEFLOW_UI_LANGUAGES`. Nothing
  in the code names `en` or `vi`.
- Every user-facing exception now goes through one base class, so a new failure that forgets
  its key fails to compile rather than printing English into a Vietnamese page.
- The bundle is a single file per language, which is easy to diff and to check: a unit test
  asserts the two languages carry the same keys, that every key a template or a constraint
  asks for exists, and that every pattern with a placeholder can actually be filled in.
- Two sentences in the bundle carry `<strong>` and `<code>`, because splitting them would
  have made them untranslatable. They are rendered with `th:utext`, and the markup is
  ReleaseFlow's own wording — never anybody's input.
- A person's language is not the language of anything they create. Approving a release still
  writes notes in the Organization's release note languages, whichever language the approver
  was reading the page in.
