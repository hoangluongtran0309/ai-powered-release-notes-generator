# ADR-0009: Automatic AI classification and neutral summaries

- Status: Accepted
- Date: 2026-09-14
- Supersedes: [ADR-0004](0004-openai-classification-as-reviewed-suggestion.md)

## Context

ADR-0004 made AI a person-initiated suggestion for Unknown changes, with one
provider, and required review of every AI result. Release notes need more than
a category: every change should carry a short, audience-neutral description,
written once in the Organization's language. With the durable worker from
ADR-0008, the AI call can run automatically outside the webhook request. Teams
also asked for providers other than OpenAI. Reviewing every AI-touched change
would make automatic AI pointless, but the rules and review triggers must keep
their authority.

## Decision

- When a provider is configured, the change worker asks the AI once for every
  new change, after the changed files are known. It sends the title, labels,
  target branch, at most 4000 characters of the description, the
  Organization's output language, and the category the rules locked, if any.
  The author is not sent.
- The answer follows one JSON contract for every provider: a category, a
  breaking flag, whether a person should review it, and a neutral core of what
  changed, why, technical detail, and a migration step. The parser rejects the
  whole answer if anything is missing or mistyped.
- Three providers implement one `AiChangeClassifier` interface, chosen with
  `RELEASEFLOW_AI_PROVIDER`:
  - `openai` uses Chat Completions with a strict JSON Schema;
  - `deepseek` uses the same API shape with `json_object` and the schema in the
    prompt;
  - `anthropic` uses the official Java SDK with Structured Outputs
    (`output_config.format`) and SDK retries disabled.
  A selected provider needs a key and a model. There is no default model.
  Credentials without a selected provider stop startup.
- The rules still win. A category they chose is kept. The AI can mark a change
  breaking but never clear the flag. Breaking, Unknown, review triggers, and an
  AI request for review all keep the change in review. Otherwise an AI answer
  may settle the change: AI results no longer always need review.
- Every change gets exactly one automatic AI request. The job is marked
  `CLASSIFYING` before the call. A job left `CLASSIFYING` becomes
  `FALLBACK_REQUIRED` and is completed from the recorded files without calling
  the AI again. Redeliveries never create a job.
- A failed or unusable answer becomes `ai_status = FAILED` with a fixed, safe
  message, plus a `CLASSIFIER_FALLBACK` trigger that forces review. It is never
  retried automatically. A person may ask again from the Change Inbox. The
  trigger stays, so a person still reviews that change.
- The output language is an Organization setting: a canonical BCP 47 tag,
  chosen at registration and changed by administrators. Summaries record the
  language they were written in.
- Without a provider, classification uses the rules alone, as before.

## Consequences

- Most conventional pull requests settle without a person. Pull requests the
  rules leave Unknown can be settled by the AI, unless another rule requires
  review.
- Pull request text leaves the system for every change while AI is configured.
  Operators who cannot send that data leave `RELEASEFLOW_AI_PROVIDER` empty.
- The Anthropic SDK adds OkHttp, the Kotlin standard library, and Jackson 2 to
  the runtime. The CI dependency review and image scan cover them.
- Changes recorded before this decision keep their state. An Unknown one can
  still be sent to the AI once by a person.
- Audience narratives, category suggestions, context sufficiency, and linked
  issues extend the same single request later.
