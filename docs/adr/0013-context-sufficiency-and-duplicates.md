# ADR-0013: Context sufficiency and possible duplicates

- Status: Accepted
- Date: 2026-09-15

## Context

The AI summarizes every pull request the same way, including ones that say
almost nothing, such as a title of "fix" and an empty description. For those it
tends to write plausible filler, which can reach a release note unnoticed.
Nothing notices either when two pull requests do the same thing, so both can
appear in a note. Reviewers need these cases pointed out, with evidence, without
the application deciding for them.

## Decision

- **Evidence only.** Both signals only add a need for review, like every other
  review trigger. Neither clears review, merges changes, nor removes one.
- **Context sufficiency.**
  - The single AI request also returns `context_sufficiency`: a score from 0
    to 100 and short reason codes for missing evidence. The request states the
    threshold (`RELEASEFLOW_CONTEXT_THRESHOLD`, default 60, checked at
    startup).
  - Fixed caps can only lower the AI's score: an empty description caps it at
    30 (`DESCRIPTION_MISSING`), a title shorter than 12 characters at 50
    (`TITLE_TOO_SHORT`), and a title of just fix, update, change, cleanup,
    misc, or wip with a description under 160 characters at 40
    (`GENERIC_TITLE`).
  - A score below the threshold is `INSUFFICIENT` and adds one
    `CONTEXT_INSUFFICIENT` trigger listing the reasons. A score equal to the
    threshold is sufficient.
  - Context is assessed only when the AI answered. Without AI, and after an AI
    failure, it is not assessed; a failure already forces review through
    `CLASSIFIER_FALLBACK`. Changes recorded before V15 stay unassessed.
- **Possible duplicates.**
  - When a change finishes processing, the worker compares it, inside the same
    transaction, with at most 500 other processed changes of the same Project
    received in the last 180 days.
  - The comparison uses character trigrams of the titles and of the content
    (description and what changed), after NFKC normalization, lower-casing, and
    collapsing other characters.
  - When both sides list changed files, the path overlap counts too:
    `0.40·title + 0.40·content + 0.20·paths`. Otherwise the score is
    `0.45·title + 0.55·content`.
  - A pair at or above `RELEASEFLOW_DUPLICATE_THRESHOLD` (default 0.82) is
    recorded with its evidence. At most the five most similar pairs are kept.
    The new change gets a `DUPLICATE_CANDIDATE` trigger for each; the earlier
    change is left alone.
- **Decisions.** Any member may confirm or dismiss a possible duplicate, once;
  a second decision is refused with `409`. A decision records who decided and
  when, and nothing else changes.

## Consequences

- Thin pull requests and likely duplicates reach a person before they reach a
  release note. The score and the similarity carry their evidence, so the
  reviewer can see why they were flagged.
- Deployments without AI behave as before for context. Duplicate detection
  works with or without AI, using the description when there is no summary.
- The comparison is one-way, from each newly processed change to earlier ones.
  Earlier changes are never re-checked, and editing a summary later does not
  re-run detection.
- These fixes differ from the earlier product:
  - the context trigger is written once;
  - a decision cannot be silently overwritten;
  - an unknown candidate is a 404;
  - the window follows the application clock.
