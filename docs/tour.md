# A tour of ReleaseFlow

This tour follows one release from an empty workspace to a note that people read in
their inbox, their team chat, their wiki, and a public changelog. Every picture was taken
from the Docker Compose demo stack (`docker-compose.demo.yml`) with AI classification on
(`RELEASEFLOW_AI_PROVIDER=openai`, model `gpt-4.1`), email pointed at a local
[Mailpit](https://mailpit.axllent.org/), and real Slack and Notion destinations.

The changes came from nine signed GitHub `pull_request` webhook deliveries for a made-up
repository, `acme/checkout-web`. No GitHub access token was configured, so ReleaseFlow could
not list any change's files, and every change was held for a person to review
("Changed files unavailable"). That is the documented behaviour: a missing file list
forces review. With a token, only breaking, Unknown, and sensitive-path changes wait.

The organization, people, and addresses in the pictures are fictional.

## 1. Create an organization

The first account creates the Organization and becomes its administrator. The workspace
overview then offers exactly one next step.

![Registering an organization and signing in](images/tour/register.gif)

| Registration | Workspace overview |
| --- | --- |
| ![Registration form](images/tour/01-register.jpg) | ![Workspace overview with one recommended next step](images/tour/02-overview.jpg) |

See [Members and invitations](../README.md#members-and-invitations) and
[Workspace overview](../README.md#workspace-overview).

## 2. Connect a source

A Project groups the changes of one product. Connecting a GitHub repository generates a
webhook path and a secret that is shown once and stored encrypted. The same page sets the
Organization's output language and its public changelog address.

![Creating a project and connecting a GitHub repository](images/tour/project-source.gif)

![Projects page with the output language, public changelog address, and a connected source](images/tour/03-projects.jpg)

See [Receive GitHub webhooks](../README.md#receive-github-webhooks) and
[Access tokens](../README.md#access-tokens).

## 3. Collect and classify changes

Each merged pull request lands in the Change Inbox. Fixed rules pick a category first
(a label, a `BREAKING CHANGE` footer); the AI fills in only what the rules left open,
scores whether the description gives enough context, and writes a neutral summary with a
short narrative per audience.

![Change Inbox with the source sync panel and the first change](images/tour/04-change-inbox.jpg)

![A change's AI summary, review triggers, and context score](images/tour/05-ai-summary.jpg)

See [Change Inbox](../README.md#change-inbox),
[AI classification and summaries](../README.md#ai-classification-and-summaries), and
[Review signals](../README.md#review-signals).

## 4. Review what the AI could not settle

`#107 misc tweaks` had no rule, a context score of 10, and an AI proposal for a new
category. The proposal waits for an administrator: here it is mapped onto the existing
Maintenance category, and the change is confirmed by a person.

![Mapping a proposed category and confirming the review](images/tour/human-review.gif)

See [Human review](../README.md#human-review) and [Categories](../README.md#categories).

## 5. Team, audiences, and categories

| Members and invitations | Audiences and templates | Category catalog |
| --- | --- | --- |
| ![Members and a pending invitation](images/tour/06-members.jpg) | ![An audience's intent and Mustache template](images/tour/07-audiences.jpg) | ![The category catalog](images/tour/08-categories.jpg) |

Every approved release gets one note per audience and language. An audience's
communication intent steers its AI narrative; its template turns each change into
Markdown. See [Audiences](../README.md#audiences).

## 6. Automate delivery

A rule runs its actions in order when something happens: here, when a Checkout Web release
is published. One rule posts to the public changelog, emails customers, and posts to Slack;
another files the operator note in Notion. A rule starts disabled; enabling it is where
ReleaseFlow checks that every action can actually be delivered.

![Writing an automation rule](images/tour/automation-rule.gif)

![Two enabled automation rules](images/tour/09-automation-rules.jpg)

See [Automation](../README.md#automation).

## 7. Review, approve, and publish a release

A release starts as a draft with a live preview for each audience, including a banner when
it holds a breaking change. Requesting review freezes the list; every change then needs a
decision. Here eight are approved, `#107` is rejected out of the release, and approval
writes the notes. Publishing freezes them for good.

![Reviewing each change, approving, and publishing release 1.4.0](images/tour/release-review-publish.gif)

| Draft with preview | Approved: the End user note | Published snapshot |
| --- | --- | --- |
| ![Draft release with per-audience preview](images/tour/10-release-draft.jpg) | ![Approved release with the End user note](images/tour/11-release-approved.jpg) | ![Published, immutable release notes with Markdown](images/tour/12-release-published.jpg) |

See [Releases](../README.md#releases) and [Publishing](../README.md#publishing).

## 8. Where the note went

Publishing only records that it happened; the automation runs start after it commits.
Both runs succeeded.

![Run history: Notion, public changelog, email, and Slack all succeeded](images/tour/13-automation-runs.jpg)

| Public changelog | Changelog entry |
| --- | --- |
| ![Public changelog list](images/tour/14-public-changelog.jpg) | ![Public changelog entry for 1.4.0](images/tour/15-public-changelog-entry.jpg) |

The changelog is anonymous, needs no session, and has an RSS feed at
`/changelog/{slug}/rss.xml`. See [Public changelog](../README.md#public-changelog).

| Email (Mailpit) | Notion |
| --- | --- |
| ![The release note email in Mailpit](images/tour/16-email.jpg) | ![The operator note filed as a Notion page](images/tour/17-notion.jpg) |

The contributor note reached Slack through an incoming webhook:

![The contributor note posted to a Slack channel](images/tour/22-slack.jpg)

## 9. Language, theme, and screen size

The interface language is chosen per person and never changes what ReleaseFlow writes:
the Vietnamese interface below still shows the English summaries it recorded.

| Vietnamese interface | Light theme | Narrow screen |
| --- | --- | --- |
| ![Workspace overview in Vietnamese](images/tour/18-vietnamese.jpg) | ![Workspace overview in the light theme](images/tour/19-light-theme.jpg) | ![Change Inbox on a narrow screen](images/tour/20-mobile.jpg) |

See [Interface language](../README.md#interface-language) and
[Output language](../README.md#output-language).

## 10. Watch the deployment

The demo stack's Grafana dashboard reads the private management port through Prometheus:
review rate, classification throughput and latency, AI error rate, action failures, and
deliveries nobody could confirm.

![ReleaseFlow Operations dashboard in Grafana](images/tour/21-grafana.jpg)

See [Watch a deployment](../README.md#watch-a-deployment).

## Reproduce the tour

1. Copy `.env.example` to `.env`, replace every placeholder, and set
   `RELEASEFLOW_AI_PROVIDER` with its key and model.
2. For email, start Mailpit on the demo network and point SMTP at it:

   ```bash
   docker run -d --name releaseflow-mailpit --network releaseflow-demo \
     --network-alias mailpit -p 127.0.0.1:8025:8025 axllent/mailpit
   ```

   with `RELEASEFLOW_SMTP_HOST=mailpit`, `RELEASEFLOW_SMTP_PORT=1025`, and
   `RELEASEFLOW_AUTOMATION_EMAIL_FROM` set in `.env`.
3. `docker compose -f docker-compose.demo.yml up --build`, then register at
   <http://127.0.0.1:8080/register>.
4. Connect a GitHub source and send merged `pull_request` deliveries to its webhook path,
   signed as GitHub does: `X-Hub-Signature-256: sha256=` followed by the HMAC-SHA256 of the
   raw body under the source's secret, with `X-GitHub-Event: pull_request` and a UUID in
   `X-GitHub-Delivery`.
