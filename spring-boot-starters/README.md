# Spring Boot Starters — Demos

This directory contains runnable demonstrations of custom Spring Boot starters — the same
auto-configuration mechanism used by `spring-boot-starter-web`, `spring-boot-starter-data-jpa`,
etc., applied to a small feature you might actually want to share across services.

Unlike every other category in this repo, each starter here is **two independent Maven
modules**, not one:

- `<starter>-spring-boot-starter/` — the reusable auto-configuration jar. This is the artifact
  a real project would add as a dependency; it has no knowledge of any demo.
- `spring-demo/` — a runnable Spring Boot app that depends on the starter (via the Maven
  reactor) and exercises it through a couple of REST endpoints, so its behavior is visible
  end-to-end.

| Starter | Demo | What it auto-configures |
|---|---|---|
| [`request-logging`](request-logging/) | `request-logging/spring-demo` | A servlet filter that logs every HTTP request/response (method, path, status, duration), with an on/off switch, opt-in body logging, and path exclusions |

More starters may be added here over time, each following the same two-module shape.
