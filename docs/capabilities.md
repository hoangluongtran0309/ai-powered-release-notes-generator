# Capabilities

The complete list of what ReleaseFlow does today. The [README](../README.md) has the
short version; what is deliberately left out is listed in the
[implementation status](implementation-status.md).

The application currently provides:

- an atomic Organization and administrator registration flow through REST and
  Thymeleaf;
- canonical, globally unique account email addresses and BCrypt password hashes;
- administrator and member roles, with single-use, expiring member invitations;
- session authentication, CSRF protection, form login, and POST logout;
- an authenticated session endpoint whose tenant identity comes exclusively
  from the principal;
- PostgreSQL persistence managed by Flyway migrations `V1` through `V27`;
- tenant-scoped Project creation and listing through REST and Thymeleaf;
- create-only GitHub repository, GitLab project, Linear team, and Jira Cloud
  project sources, several per Project;
- a unique webhook identity and 256-bit signing secret for each integration;
- AES-256-GCM encryption at rest with one-time secret reveal;
- signed GitHub, GitLab, and Linear webhook endpoints that record each merged
  pull request, merged merge request, and completed issue once as a normalized
  change, and show the last verified delivery per source;
- a Jira project read every five minutes for issues that moved into Done, with
  no webhook to set up;
- Jira issues mentioned by a pull or merge request's title, description, branch,
  or commits, shown on the change, given to the AI, and available to release
  note templates;
- an optional, write-only access token per source, confirmed with the provider
  and stored encrypted;
- a durable processing queue that asks each change's provider for what it can
  add — the files it touched, or the issue restated — outside the webhook
  request, with bounded retries;
- deterministic, explainable classification of every recorded change, with
  breaking, unrecognized, and sensitive-file changes always marked for human
  review, and sensitive-path patterns that administrators can add per Project;
- a per-Project Change Inbox in the UI and REST, filterable by category and
  review status;
- a category catalog per Organization, managed by administrators, with
  categories the AI proposes held for an administrator's decision;
- review signals for pull requests that give too little context and for
  changes that look like earlier ones, with the evidence shown to reviewers;
- optional automatic AI classification with OpenAI, Anthropic, or DeepSeek:
  one request per change, a neutral summary and a narrative for each audience
  in the Organization's output language, and rules and review triggers that
  always win;
- audiences managed by administrators, each with a communication intent and a
  Mustache template; three presets (operator, contributor, end user) are
  created with every Organization;
- an Organization output language, chosen at registration and changed by
  administrators;
- human review of any change, recording who confirmed or corrected its
  category and breaking flag;
- releases that move from draft through a per-change review and approval to
  publication, with several drafts per Project, a planned release time, and a
  live preview of each audience's note;
- one release note per audience written at approval, editable until
  publication, with editable change summaries that update the notes;
- release notes in up to five languages, with each change translated by DeepL
  through a durable, cached queue, per-language audience templates, and
  publication held until every note is ready;
- publication of an approved release that freezes every audience's note, with
  copyable and downloadable Markdown;
- `application/problem+json` responses with stable error codes for the current
  REST operations;
- the public home page and application status endpoint from the bootstrap
  slice;
- a Tailwind CSS, DaisyUI, and Alpine.js workspace UI with a light/dark theme;
- an English or Vietnamese interface, chosen per person, with every page,
  form message, and error explanation written in the reader's own language;
- a workspace overview with four figures and the one next step that is
  unfinished, a Project switcher the server remembers, error pages for a
  mistyped or forbidden address, and tables that become cards on a phone;
- a non-root container image and a Docker Compose demo stack with PostgreSQL,
  Prometheus, and Grafana, with metrics on a private management port;
- automation rules that deliver a published release note to a GitHub Release, a
  Slack channel, a list of email addresses, a Notion page, a Confluence Cloud
  space, a Microsoft Teams chat, or a Zendesk help centre, in an order the rule
  fixes, with a
  durable run history, an outcome nobody may repeat without confirming it, and no
  way for a rule to hold up the release that triggered it;
- GitHub Actions gates for tests, CodeQL, dependency review, secret scanning,
  and container vulnerability scanning.
