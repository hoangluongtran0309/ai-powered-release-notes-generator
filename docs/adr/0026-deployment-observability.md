# ADR-0026: Deployment observability

- Status: Accepted
- Date: 2026-09-21
- Amends: [ADR-0007](0007-ci-and-container-supply-chain.md), whose health check calls the
  public `GET /api/status` "because there is no private management port yet" and whose
  Compose stack "runs only the application and PostgreSQL". Both are now out of date, in
  the way that ADR expected.

## Context

ReleaseFlow could be run but not watched. The only signal a deployment had was that the
web server answered, which says nothing about whether changes are being classified, how
long people wait for a release note, how often the AI refuses, or how many deliveries
ended in a state nobody could confirm.

Those four questions are operational, not analytical. They are about the deployment as a
whole, and answering them must not become a way of learning about the Organizations inside
it — which is the whole risk of adding metrics to a multi-tenant application, because a
label is a permanent, unauthenticated read of whatever is put in it.

## Decision

- **Actuator answers on its own port, and the port is private.**
  `management.server.port` defaults to 8081 bound to `127.0.0.1`. A deployment may bind it
  to a private address — the Compose stack does, inside a network marked `internal: true` —
  but it is never a product endpoint. Only `health` and `prometheus` are exposed and health
  details are hidden, so the port says whether the application is well and what its
  aggregate counters are, and nothing else.
- **A security chain of its own, matched by asking the actuator.**
  `EndpointRequest.toAnyEndpoint()` rather than the path `/actuator/**`: a path matcher
  would be correct only by the accident that the endpoints are served elsewhere, and would
  quietly start protecting the wrong thing if the base path or the port ever changed. The
  chain permits `GET` on the two endpoints, denies everything else, creates no session —
  a scrape every fifteen seconds would otherwise fill the session store with nobody — and
  names its CSRF exemption as ADR-0023 requires.
- **Four metrics, and every label comes from a finite set.**
  `releaseflow.classification.completed` by `needs_human_review`;
  `releaseflow.classification.collect_to_complete`, a timer over the whole wait from a
  change arriving to its classification being committed;
  `releaseflow.classification.provider.requests` by `provider` and `outcome`; and
  `releaseflow.automation.action.executions` by `trigger` and `outcome`. **No label ever
  carries an Organization, Project, release, rule, run, action, model, external reference,
  or the text of an error.** That is not a convention to remember: the series are built
  from enums at startup, so there is nowhere for such a value to go, and both unit tests
  assert that the set of label names is exactly what is listed here.
- **Every series is registered at zero when the application starts.** A panel then shows a
  line at zero rather than "no data", a rate never divides by a series that is not there,
  and CI can assert that all four metric families are being scraped without first making
  the application do work.
- **A count follows the write, never precedes it.** A classification is counted after the
  transaction that committed it returns, so a rollback or a claim that went stale counts
  nothing. A delivery is counted after the outcome is durable, and only when this worker
  still held the claim. An action abandoned by a stopped worker is counted as `unknown`
  when recovery marks it so — otherwise the unknown rate would understate exactly the
  case an operator most needs to see.
- **`FAILED` and `UNKNOWN` stay apart, and the failure rate is `failed / (succeeded +
  failed)`.** An unknown delivery may have arrived. Folding it into the failure rate would
  report something untrue about a page or a message that may be sitting in front of
  somebody.
- **Provider requests are counted by wrapping the provider, not by editing each one.**
  One decorator around the configured classifier gives exactly one sample per request for
  all three providers and for every caller, including a person retrying by hand, and
  counts a reply that arrived but could not be read as the error it was.
- **The health check now asks the management port**, which reports the database too, so a
  container whose database has gone is unhealthy rather than merely answering.
- **The demo stack provisions Prometheus and Grafana, and is a demonstration.** Seven days
  of retention, one dashboard, both published on host loopback only, Grafana's
  administrator password required like every other Compose secret. Alerting, durable
  storage, retention policy, and access control are a deployment's own to bring.

## Consequences

- Counters restart at zero when the process does. Every panel uses `rate` or `increase`,
  which is what those functions are for; nothing here is an accounting record, and the
  database remains the only place that keeps one.
- A question about one Organization cannot be answered from metrics, by design. It needs a
  product feature with an authorization story, not a label.
- The image now declares two ports, and the Compose stack must keep 8081 unpublished. CI
  asserts that on every run, next to its existing assertion about the database port.
- A deployment that runs several replicas gets one set of counters per replica, summed by
  Prometheus. The latency quantiles are computed from the shared buckets rather than per
  process, so they remain meaningful when summed.
