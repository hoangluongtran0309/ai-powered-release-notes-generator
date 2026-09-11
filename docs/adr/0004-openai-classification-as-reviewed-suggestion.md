# ADR-0004: OpenAI classification as a reviewed suggestion

- Status: Accepted
- Date: 2026-09-11

## Context

Deterministic rules classify most merged pull requests, but changes without a
Conventional Commit title type or a familiar label stay Unknown. ReleaseFlow
adds one AI provider, OpenAI, to help with those changes. It is the first
network call the application makes. The product invariants require that AI
failures become explicit review states, that network I/O never runs inside a
database transaction, and that a person decides what ships.

## Decision

- Only changes the rules left Unknown can be sent to OpenAI. Rule results are
  explainable and are never replaced by a model answer.
- A person starts each request from the Change Inbox, or through
  `POST /api/projects/{projectId}/changes/{changeId}/ai-classification`. The
  webhook never calls OpenAI, and there is no background worker, queue, or
  automatic retry. After a failure, the person can press the button again.
- The request uses the Chat Completions API with a strict JSON Schema
  (Structured Outputs). The schema allows the same categories as the rules,
  plus `unknown`, a breaking flag, and a short rationale. The request sets
  `store: false`. It sends only the title, labels, target branch, and at most
  4000 characters of the description. The author is not sent. The prompt tells
  the model to treat pull request text as data.
- An AI result is a suggestion. The change keeps `needs_review = true`, and
  the AI can add a breaking flag but never remove one. A database check
  constraint enforces both.
- A failed attempt is stored as `ai_status = FAILED` with a fixed, safe
  message. The change stays Unknown and in review.
- The service reads the change in one short transaction, calls OpenAI with no
  transaction open, and writes the outcome in a second transaction. The
  client refuses to run inside an active transaction.
- The client is a direct `RestClient` class with no provider interface. The
  API key and model come only from the environment. The model has no default,
  and AI stays disabled unless both values are present.

## Consequences

- No new dependency is needed, and tests use a local HTTP stub instead of the
  real API.
- Reviewers see the model's category, breaking flag, and rationale next to
  the rule reasons, but no AI output bypasses review.
- Merged pull request text leaves the system when a person asks for a
  suggestion. Operators who cannot send that data can leave AI disabled.
- A second provider, automatic classification, or retries would each need a
  new decision rather than an extension point prepared now.
