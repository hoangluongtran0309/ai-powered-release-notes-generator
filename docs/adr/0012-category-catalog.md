# ADR-0012: Category catalog per Organization

- Status: Accepted
- Date: 2026-09-15

## Context

Categories were a fixed enum of six values: feature, fix, performance,
documentation, maintenance, and unknown. The rules, the AI prompt and schema,
reviews, the Change Inbox, release decisions, and the sectioning of release
notes (ADR-0011) all used it, and PostgreSQL checked it. Teams need their own
categories, such as security or compliance. When nothing fits, the AI should
be able to say so without inventing a category that silently changes what
reviewers and readers see.

## Decision

- **Catalog.** Each Organization has a category catalog in
  `category_definitions`.
  - Each category has a code (`[A-Z][A-Z0-9_]*`, at most 64 characters,
    unique within the Organization, fixed once created), a display name, and a
    group.
  - A category is active or archived. Archiving is reversible.
  - `UNKNOWN` is the one system category: it is always active, always in the
    OTHER group, and can only be renamed.
- **Groups.** There are six fixed groups: FEATURE, FIX, PERFORMANCE,
  DOCUMENTATION, MAINTENANCE, and OTHER (option D5, with its own performance
  group). Release notes are sectioned by group. Breaking stays a separate flag;
  there is no breaking group or category.
- **Seed.** Every Organization starts with the former values as categories:
  FEATURE, FIX, PERFORMANCE, DOCUMENTATION, MAINTENANCE, and UNKNOWN.
  Registration seeds them through the `OrganizationRegistered` event, and V14
  seeds them for existing Organizations. Existing changes keep their codes,
  so the REST category values do not change.
- **Snapshot.** A change stores a snapshot of its category: code, display
  name, and group. Renaming, regrouping, or archiving a category never rewrites
  changes that already carry it.
- **Rules.** The fixed rules (title type, labels, documentation-only files,
  breaking footer) each name a group and a preferred code.
  - The preferred category is used if it is active. Otherwise the first active
    category of the group, by code, is used.
  - A group without an active category locks nothing, and the AI or a person
    chooses.
- **AI.** The AI chooses exactly one active code; the response schema lists
  the codes for each request. A code outside the catalog is an invalid answer
  and becomes the usual fallback.
  - When nothing fits, the AI returns UNKNOWN with a proposed category (code,
    name, group, rationale) and asks for review.
  - The change stays Unknown and gets a `CATEGORY_SUGGESTION_PENDING` trigger,
    and the proposal is stored in `category_suggestions`.
  - A proposal is ignored when the rules locked the category, when its code is
    invalid, or when the code is already listed.
- **Suggestions.** Only an administrator decides a proposal.
  - APPROVED adds the proposed category, or restores an archived category with
    that code.
  - MAPPED chooses an existing active category.
  - REJECTED adds nothing.
  - A decision is final. The change, if still Unknown and unreviewed, takes
    the approved or mapped category with the source `SUGGESTION`, but it still
    needs a person's review. Only a recorded review clears that need.
- **Review.** Reviews in the Change Inbox and release decisions accept only
  active catalog codes other than UNKNOWN, ignoring case.
- **Access.** Every member can read the catalog. Only administrators change it
  or decide proposals.
- **Database.** PostgreSQL checks the code format, the group values, the
  system category rules, the snapshot on changes (UNKNOWN is always in group
  OTHER), the SUGGESTION source (it needs a successful AI attempt), and the
  consistency of each suggestion's decision.

## Consequences

- Adding a category needs no code change. The next AI request can choose it,
  and reviewers can pick it.
- The digest and the grouped preview section by group, so a custom category
  appears under its group's heading.
- A category the AI proposes never reaches a release note without an
  administrator's decision and a person's review.
- `change` and `release` depend on the new `category` package, which depends
  on neither. Decisions reach changes through a `CategorySuggestionDecided`
  event handled in the decision's transaction.
- There is no separate endpoint that assigns a category without reviewing the
  change. The Inbox review and the release decision already assign catalog
  categories, and they are what clears review.
- The category part of [ADR-0009](0009-automatic-ai-classification.md) (a
  fixed list of six values) is superseded. Its other rules stand: the rules
  win, AI failures fall back, and there is one request per change.
