# Request-Logging Spring Boot Starter Design

**Date:** 2026-07-15
**Status:** Approved

## Overview

A new top-level `spring-boot-starters/` category (sibling to `message-brokers/`, `noSQL/`, `cqrs-event-sourcing/`, `template-engines/`, `distributed-transactions/`), containing the repo's first custom Spring Boot starter: **`request-logging`**. Unlike every other category, a starter demo is inherently two artifacts, not one — the reusable autoconfiguration jar a real project would depend on, and a separate demo app that consumes it via the Maven reactor to prove it actually works end-to-end. Keeping them as separate modules is the point: the starter has zero knowledge of the demo, and the demo only sees the starter's public contract (configuration properties + an auto-registered filter), never its internals.

The starter auto-configures a servlet `Filter` that logs every incoming HTTP request and its response (method, path, status, duration), with three configuration knobs: a master on/off switch, an opt-in for logging request/response bodies, and a list of path patterns to exclude (e.g. Actuator's own endpoints, so health-check polling doesn't spam the log).

## Repository structure

```
spring-boot-starters/
├── pom.xml                                          (new parent POM, packaging=pom, mirrors distributed-transactions/pom.xml)
├── eclipse-formatter.xml                            (copy — same style repo-wide)
├── README.md                                        (category overview: what a starter is, why it's two modules)
└── request-logging/
    ├── request-logging-spring-boot-starter/
    │   ├── pom.xml                                  (packaging: jar, no spring-boot-maven-plugin, no main class)
    │   └── src/
    │       ├── main/
    │       │   ├── java/com/testingai/logging/autoconfigure/
    │       │   │   ├── RequestLoggingProperties.java        (@ConfigurationProperties("app.logging.request"))
    │       │   │   ├── RequestLoggingFilter.java             (OncePerRequestFilter)
    │       │   │   └── RequestLoggingAutoConfiguration.java  (@AutoConfiguration)
    │       │   └── resources/META-INF/spring/
    │       │       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    │       └── test/java/com/testingai/logging/autoconfigure/
    │           └── RequestLoggingAutoConfigurationTest.java  (ApplicationContextRunner)
    └── spring-demo/
        ├── pom.xml                                  (artifactId: request-logging-demo; depends on the starter module)
        ├── README.md
        └── src/
            ├── main/
            │   ├── java/com/testingai/logging/demo/
            │   │   ├── RequestLoggingDemoApplication.java
            │   │   ├── DemoController.java                    (GET /demo/hello, POST /demo/echo)
            │   │   └── EchoRequest.java                        (record: message)
            │   └── resources/application.yml                  (port 8090; app.logging.request.include-body: true;
            │                                                     app.logging.request.excluded-paths: [/actuator/**])
            └── test/java/com/testingai/logging/demo/
                ├── RequestLoggingDemoApplicationTest.java
                └── RequestLoggingIntegrationTest.java          (MockMvc; asserts logged output via a Logback ListAppender)
```

### Cross-cutting fixes needed in existing files

- **`.githooks/pre-commit`** — extend the staged-file grep to also match `^spring-boot-starters/.*\.java$`, and add a matching `mvn spotless:apply` block run from `spring-boot-starters/`.
- **`CLAUDE.md`** — add a "Spring Boot starter demo" command section (mirroring the Saga pattern section), a `spring-boot-starters/` row in the repository layout table, and a line noting no infrastructure/docker is required.

## The starter's contract

```java
@ConfigurationProperties("app.logging.request")
public record RequestLoggingProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("false") boolean includeBody,
    @DefaultValue("/actuator/**") List<String> excludedPaths
) {}
```

Immutable, constructor-bound `@ConfigurationProperties` as a record — Spring Boot applies `@DefaultValue` per-component during binding, so no compact constructor or setters are needed.

`RequestLoggingFilter extends OncePerRequestFilter`:
- Always logs `method`, `requestURI`, response `status`, and `durationMs` at `INFO`, for any path not matching `excludedPaths` (checked via `AntPathMatcher`).
- When `includeBody` is true, wraps the request/response in `ContentCachingRequestWrapper`/`ContentCachingResponseWrapper` so the body can be read after the filter chain completes, and logs a (reasonably truncated) body snippet. When false, no wrapping occurs — no body-caching overhead for the common case.

`RequestLoggingAutoConfiguration`:
- `@AutoConfiguration`, `@ConditionalOnWebApplication(type = SERVLET)`, `@EnableConfigurationProperties(RequestLoggingProperties.class)`.
- One `@Bean @ConditionalOnMissingBean @ConditionalOnProperty(prefix = "app.logging.request", name = "enabled", matchIfMissing = true)` method producing `RequestLoggingFilter` — absent entirely when disabled, and a consumer's own `RequestLoggingFilter` bean silently wins over the starter's.
- Registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (the Spring Boot 3.x mechanism; the legacy `spring.factories` key is not used).

## Demo app

Single `DemoController`, consistent with every other module in the repo:

| Endpoint | Behavior |
|---|---|
| `GET /demo/hello` | Returns a static greeting. Exercises the always-on request-line logging (method/path/status/duration). |
| `POST /demo/echo` | Body: `EchoRequest{message}`. Echoes it back. Exercises body logging, since `application.yml` turns `includeBody` on for this demo. |

`application.yml` also adds `spring-boot-starter-actuator` and sets `excludedPaths: [/actuator/**]` purely to give the exclusion knob something real to demonstrate — hitting `/actuator/health` produces no log line, while `/demo/*` calls do.

Swagger UI at `/swagger-ui/index.html`, matching repo convention.

## Testing

- **Starter** — `RequestLoggingAutoConfigurationTest` using `ApplicationContextRunner` (no servlet container needed):
  - filter bean present by default;
  - filter bean absent when `app.logging.request.enabled=false`;
  - a user-supplied `RequestLoggingFilter` bean wins (`@ConditionalOnMissingBean` backs off).
- **Demo** — `RequestLoggingIntegrationTest`: `@SpringBootTest` + `MockMvc` (plain JUnit5/Spring, no Spock, per [[spock-spring-webmvctest-incompatibility]]) attaches a Logback `ListAppender` to capture log output, then asserts: `GET /demo/hello` produces a log line with status 200; `POST /demo/echo` produces a log line containing the request body; `GET /actuator/health` produces no log line from the filter.
- No `util/FailureSimulator` — there's nothing to simulate failures for; every request either produces a log line or doesn't, deterministically, based on configuration.
- No Gatling simulation — this category is about auto-configuration behavior, not throughput, and the demo app has no meaningful load-bearing logic to benchmark. (If a future starter in this category has performance-sensitive behavior worth measuring, that module can add one; this one does not.)

## Ports

- `request-logging/spring-demo` → `8090` (next free slot after `distributed-transactions/saga`'s `8089`).

## Spring Boot configuration

**Spring Boot version:** 3.4.x
**Java:** 21

**`request-logging-spring-boot-starter` dependencies:** `spring-boot-autoconfigure`, `spring-boot-starter-web` (needed for `OncePerRequestFilter` and `ContentCachingRequestWrapper`), `spring-boot-configuration-processor` (optional, compile-time only — generates IDE property metadata, no runtime effect), `spring-boot-starter-test` (test).

**`request-logging-demo` dependencies:** the starter module (via reactor), `spring-boot-starter-web`, `spring-boot-starter-actuator`, `springdoc-openapi-starter-webmvc-ui`, `lombok`, `spring-boot-starter-test` (test). No Gatling dependency, since this module has no performance test.

## README

`spring-boot-starters/README.md` is a short category index explaining what a custom Spring Boot starter is, why the module is split into two Maven artifacts, and a table of starters (just `request-logging` for now, room to grow).

`spring-boot-starters/request-logging/spring-demo/README.md` follows the existing per-module format: prerequisites (Java 21, Maven — no Docker needed), run instructions (`mvn spring-boot:run`), a short explanation of the three properties and their defaults, and `curl` walkthroughs showing: the default happy path, a body-logging example via `/demo/echo`, and disabling the filter entirely via `app.logging.request.enabled=false` to show the log lines disappear. Swagger UI link included.

## Scope limits

- No async/reactive (WebFlux) support — the filter is a classic `OncePerRequestFilter`, servlet-stack only; a reactive equivalent (`WebFilter`) is a natural but separate future addition, not in scope here.
- No log level configuration (always `INFO`) — configurable log levels would be a reasonable real-world addition but add a fourth property without adding to the core teaching point (conditional auto-configuration + property binding).
- No request/response header logging — only method, path, status, duration, and (optionally) body; headers can carry sensitive data (auth tokens, cookies) and redaction is its own can of worms, out of scope for this demo.
- No max-body-size / truncation configuration — a fixed, reasonable truncation length is hardcoded rather than exposed as a property, to keep the properties surface to the three that were explicitly asked for.
- Only one starter in the new category for now — the category and parent POM are built to hold more (e.g. a correlation-ID starter, a caching starter) but none are built speculatively here.
