# Swagger UI Configuration — Design Spec

**Date:** 2026-06-10
**Scope:** `message-brokers/rabbitmq/spring-demo/`

---

## Goal

Add Swagger UI to the RabbitMQ demo project so all four messaging-pattern endpoints are browsable and executable from `http://localhost:8080/swagger-ui.html`.

---

## Approach

Option B — annotations on existing files, no separate config class:
- `springdoc-openapi-starter-webmvc-ui` dependency provides the UI and auto-discovery
- `@OpenAPIDefinition` on `RabbitMqDemoApplication` sets API-level metadata
- `@Tag`, `@Operation`, `@Parameter` on `DemoController` provide human-readable descriptions

---

## Changes

### 1. `pom.xml`

Add dependency:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.6</version>
</dependency>
```

### 2. `RabbitMqDemoApplication.java`

Add `@OpenAPIDefinition`:

```java
@OpenAPIDefinition(info = @Info(
    title = "RabbitMQ Demo API",
    version = "1.0.0",
    description = "Learning project demonstrating RabbitMQ messaging patterns"
))
@SpringBootApplication
public class RabbitMqDemoApplication { ... }
```

### 3. `DemoController.java`

**Class level:**
```java
@Tag(name = "RabbitMQ Demo", description = "Triggers for the four RabbitMQ messaging patterns")
```

**Endpoint annotations:**

| Method | `@Operation` summary | `@Parameter` details |
|---|---|---|
| `simple` | "Send to simple queue" | `message`: "Text to send to the simple queue" |
| `work` | "Send to work queue" | `message`: "Text to send; add dots to simulate work duration (e.g. `task..`)"; `count`: "Number of messages to dispatch (default 5)" |
| `pubsub` | "Broadcast via fanout exchange" | `message`: "Text to broadcast to all fanout subscribers" |
| `routing` | "Route via direct exchange" | `key`: "Routing key — one of: info, warning, error", `@Schema(allowableValues = {"info","warning","error"})`; `message`: "Text to route" |

---

## Endpoints After Change

| URL | Purpose |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Interactive Swagger UI |
| `http://localhost:8080/v3/api-docs` | Raw OpenAPI JSON |

---

## Testing

No new unit tests required — springdoc auto-configuration is library-owned. Existing `DemoControllerTest` (`@WebMvcTest`) continues to pass unchanged because springdoc does not affect the MockMvc test slice.
