# Request-Logging Spring Boot Starter — Demo

A Spring Boot app that consumes `request-logging-spring-boot-starter` to demonstrate a custom
auto-configured servlet filter that logs every HTTP request and response: method, path, status,
and duration, with three configuration knobs.

## Prerequisites

- Java 21
- Maven 3.9+

All commands below assume your working directory is `spring-boot-starters/` (the reactor
root) — `spring-demo` depends on the starter as a Maven sibling, so it must build inside the
same reactor rather than in isolation.

## Run the app

```bash
mvn spring-boot:run -pl request-logging/spring-demo -am
```

## The starter's properties

| Property | Default | Effect |
|---|---|---|
| `app.logging.request.enabled` | `true` | Master on/off switch for the filter |
| `app.logging.request.include-body` | `false` | Also log (truncated) request/response bodies |
| `app.logging.request.excluded-paths` | `/actuator/**` | Ant-style path patterns to skip entirely |

This demo's `application.yml` turns `include-body` on and keeps the default `excluded-paths`,
so Actuator's own health checks never show up in the log.

## Try it

```bash
# Always-on request-line logging
curl -s http://localhost:8090/demo/hello
# => log line: GET /demo/hello -> 200 (N ms)

# Body logging (include-body: true in this demo's config)
curl -s -X POST http://localhost:8090/demo/echo \
  -H "Content-Type: application/json" \
  -d '{"message":"hello starter"}'
# => log line also includes requestBody={"message":"hello starter"} responseBody={"message":"hello starter"}

# Excluded path — no log line at all
curl -s http://localhost:8090/actuator/health

# Turn the filter off entirely
mvn spring-boot:run -pl request-logging/spring-demo -am -Dspring-boot.run.arguments=--app.logging.request.enabled=false
# repeat the first curl — no log line this time
```

## Swagger UI

http://localhost:8090/swagger-ui/index.html

## Tests

- `request-logging-spring-boot-starter`: `RequestLoggingFilterTest` (filter behavior in
  isolation) and `RequestLoggingAutoConfigurationTest` (conditional wiring, via
  `ApplicationContextRunner`).
- `spring-demo`: `RequestLoggingIntegrationTest` (`MockMvc` + a Logback `ListAppender`, proving
  the auto-configured filter is actually active end-to-end).

Run all of them with `mvn test` from `spring-boot-starters/`.
