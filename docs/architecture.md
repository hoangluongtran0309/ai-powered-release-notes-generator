# Architecture

## Implemented shape

ReleaseFlow is one Spring Boot application built from one Maven module and
packaged as one executable JAR. It currently has one small `status` capability:

```text
GET /                 -> HomeController   -> Thymeleaf home view
GET /api/status       -> StatusController -> JSON status response
```

There is no database, authentication, tenant model, external service call,
background worker, or separately deployed frontend in the implemented system.
The static stylesheet is served by Spring Boot alongside the Thymeleaf view.

## Development direction

New code is grouped by product capability. A capability starts with direct,
readable classes and gains internal layers only when its implemented behavior
needs them. REST and Thymeleaf entry points will share the same application
behavior rather than call each other.

Security, tenant isolation, durable persistence, AI failure handling, and
immutable publication are requirements for future slices, not claims about the
current baseline. Their state is tracked in
[implementation-status.md](implementation-status.md).
