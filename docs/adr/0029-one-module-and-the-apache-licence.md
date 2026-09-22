# ADR-0029: One module, and the Apache licence

- Status: Accepted
- Date: 2026-09-22
- Settles: decision **D1** of `feature-parity-slice-plan.md`, which had held two separate
  questions together since the plan was written: whether to split the codebase the way the
  earlier project did, and what licence to publish under.

## Context

The earlier project shipped two Maven modules — `release-notes-open-core` under
AGPL-3.0-only and `release-notes-enterprise` under a proprietary licence — and put its
template, automation and distribution capabilities in the second one. ReleaseFlow has been
one module since the bootstrap slice, and D1 recorded that as provisional: "parity of
function, not of structure; if it ever needs to be commercialised, do it in S24".

Meanwhile the repository has been **public with no LICENSE file at all** since its first
commit. That is not a neutral state. With no licence, the default is all rights reserved:
nobody may copy, modify or run it, and a reader cannot tell whether that is intended or an
oversight. For a public project meant to be read, that is a defect, not a deferral.

## Decision

- **One Maven module, and the split is abandoned rather than deferred.** The capabilities
  the earlier project sold separately — audiences and templates, automation, the public
  changelog — sit beside every other capability, packaged by product capability as
  `AGENTS.md` requires. There is no commercial plan that a compile-time boundary would
  serve, and a boundary maintained for a plan nobody has is a cost with no payer. This
  moves out of "deliberately deferred", which implies a decision still to come, and into
  a decision taken.
- **Apache License 2.0, for the whole repository.** The file is the official text from
  `apache.org`, with the appendix filled in; nothing is paraphrased.
- **The licence and the notice travel with the artifact.** They are copied into
  `META-INF/` of the JAR, so somebody who has only the binary has both.
- **`SECURITY.md` says how to report a weakness privately**, because the project holds
  webhook secrets, provider tokens and action credentials, and answers unauthenticated
  webhooks. It also says what this project is — one person, no rota, no bounty — so a
  reporter knows what to expect rather than guessing.

### Why Apache-2.0 rather than AGPL-3.0-only

- **The dependency tree does not force copyleft.** It is Apache-2.0 and MIT almost
  throughout. Every artifact that lists a GPL-family licence offers another: Logback under
  EPL-2.0 or LGPL-2.1, the Jakarta APIs under EPL-2.0 or GPL-2.0 *with the Classpath
  Exception*, JNA and Javassist under Apache-2.0 among others. Nothing in the tree
  restricts what this project may be licensed as, so the choice is genuinely free and has
  to be made on its merits.
- **It is the licence of this ecosystem.** Spring Boot, Spring Security, Flyway,
  Micrometer and Testcontainers are all Apache-2.0. Matching them is the answer a reader
  expects and does not have to think about.
- **It grants patent rights explicitly** (§3), which MIT does not. For a public project
  under one person's name, saying so is better than leaving it to be inferred.
- **AGPL's protection is against a use nobody plans to make.** Its network clause matters
  when somebody would run your work as a service without sharing changes back. There is no
  commercial interest here to protect, and the clause has a real cost: many organisations
  will not let their staff read, fork or reuse AGPL code without asking a lawyer first,
  which is exactly the audience a public project like this is written for.

## Consequences

- Anybody may use, modify and redistribute ReleaseFlow, including commercially, provided
  they keep the licence and the notice and state what they changed.
- Relicensing later remains possible without asking anybody: every commit is by one author,
  so there is no contributor whose agreement would be needed. Going from Apache-2.0 to a
  copyleft licence later is allowed; it would not retroactively cover copies already taken.
- `S24` is now only release automation. The module split is no longer part of it.
- Source files carry no per-file licence header. `LICENSE` and `NOTICE` at the root, and
  inside the artifact, are what Apache-2.0 asks for; a header in every file would be noise
  in a codebase with one author and one licence.
