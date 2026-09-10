# ADR-0003: Tailwind, DaisyUI, and Alpine for server-rendered pages

- Status: Accepted
- Date: 2026-09-10

## Context

The first slices shipped a small hand-written stylesheet. The pages worked but
did not match the workspace design the product is converging on: a split
sign-in screen, a sidebar application shell, cards, alerts, and a dark theme.
Reproducing that design by hand would duplicate a component library, and the
project had deliberately deferred any Node.js tooling until a slice needed it.

## Decision

Build the stylesheet with Tailwind CSS 4 and DaisyUI 5 through PostCSS. The
Maven build installs a pinned Node.js and npm into the project directory with
`frontend-maven-plugin`, runs `npm ci`, and compiles
`src/main/resources/static/css/application.css` into `target/classes`. Maven
resource copying excludes that source file so it never overwrites
the generated one. No system Node.js installation is required.

Compose pages with the Thymeleaf Layout Dialect (`layout/auth` and
`layout/main`). Load Alpine.js from a WebJar for presentation-only behavior:
theme switching, the user menu, password visibility, and copy buttons.

Forms keep posting to the existing Thymeleaf controllers and render validation
errors on the server. Alpine never calls the REST API, so REST and UI continue
to share the same application services and CSRF policy.

## Consequences

- Page markup uses Tailwind utilities and DaisyUI component classes; only the
  classes templates reference are emitted.
- The first build downloads Node.js and npm packages; later builds reuse the
  local `node/` and `node_modules/` directories, which are not committed.
- Browsers without JavaScript still submit every form; only the theme toggle,
  menu, password toggle, and copy buttons depend on Alpine.
- Adding a client-rendered page, bundler, or JavaScript test runner requires a
  new decision.
