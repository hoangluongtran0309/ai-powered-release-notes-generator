# ADR-0015: Release notes in several languages

- Status: Accepted
- Date: 2026-09-15

## Context

Approval writes one note per audience, in the Organization's output language.
An Organization whose readers speak English and Vietnamese has to rewrite every
note by hand. The AI writes each change's summary and narratives once, in one
language. Asking it again per language would repeat the classification cost,
and approval must never wait on the network.

## Decision

- **Release note languages.** An administrator chooses one to five target
  languages. Approval writes one note per audience and language. Without a
  choice the output language is the only one, and nothing changes.
- **Templates per language.** An audience keeps its main template, and has a
  variant for each target language that the output language does not cover.
  - Variants are made when a language or an audience is added. A shipped
    audience starts from its shipped template in that language; any other
    audience starts from a copy of its main template.
  - Administrators edit variants. Resetting a shipped audience resets them.
- **Translate content, not notes.** DeepL translates a change's four summary
  fields and its narratives, never a whole note. The template, the digest
  labels, and the pull request title stay out of it.
- **A durable queue after commit.**
  - Approval only records a job per change, target language, and input hash.
  - A worker translates jobs after the transaction commits, outside any
    transaction.
  - The same input is never translated twice, and every translated text is
    cached per Organization and language pair.
- **Bounded retries.**
  - A rate limit, a server error, or a network failure is retried, up to five
    attempts with backoff. Any other failure is final.
  - Without a provider, a job fails at once.
  - A person can retry failed jobs.
- **Readiness.**
  - A note shows untranslated text until its translations arrive, and is
    `PENDING`, or `FAILED` when one failed.
  - Editing a note by hand makes it `READY`.
  - A release is published only when all its notes are ready. The service and
    a database trigger both enforce this.
- **Provider.** `RELEASEFLOW_TRANSLATION_PROVIDER` is `disabled` (the
  default) or `deepl`, with `RELEASEFLOW_DEEPL_API_KEY`. A key without the
  provider stops startup.

## Consequences

- One approval gives every reader a note in their language, and publication
  cannot outrun the translations.
- Translation costs one DeepL call per new change text. Repeated texts and
  re-approvals are free, and approval stays fast and offline.
- Machine translation can be wrong, and nothing checks it before publication.
  Reviewers read the translated notes, and can edit them, before publishing.
- A deployment without DeepL can still choose several languages. Those notes
  fail at once, and a person must write them by hand.
- These fixes differ from the earlier product:
  - failed jobs can be retried;
  - language templates are made for existing audiences;
  - the database enforces readiness at publication.
