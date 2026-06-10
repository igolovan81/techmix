# Swagger UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Swagger UI to the RabbitMQ demo so all four messaging endpoints are browsable and executable at `http://localhost:8080/swagger-ui.html`.

**Architecture:** Add `springdoc-openapi-starter-webmvc-ui` which auto-discovers Spring MVC endpoints. API-level metadata (`@OpenAPIDefinition`) goes on the main application class. Per-endpoint documentation (`@Tag`, `@Operation`, `@Parameter`) goes directly on `DemoController`. No separate config class needed.

**Tech Stack:** springdoc-openapi-starter-webmvc-ui 2.8.6, Swagger/OpenAPI 3 annotations (`io.swagger.v3.oas.annotations.*`)

---

## File Map

- Modify: `message-brokers/rabbitmq/spring-demo/pom.xml`
- Modify: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/RabbitMqDemoApplication.java`
- Modify: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/controller/DemoController.java`

---

### Task 1: Add springdoc dependency and API-level metadata

**Files:**
- Modify: `message-brokers/rabbitmq/spring-demo/pom.xml`
- Modify: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/RabbitMqDemoApplication.java`

Note: annotations are declarations, not logic — no new unit tests are needed. Verification is `mvn compile` (compile-time) and `mvn test` (existing tests must stay green).

- [ ] **Step 1: Add springdoc dependency to `pom.xml`**

In `message-brokers/rabbitmq/spring-demo/pom.xml`, add inside the `<dependencies>` block (after the existing dependencies, before `</dependencies>`):

```xml
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.8.6</version>
        </dependency>
```

- [ ] **Step 2: Add `@OpenAPIDefinition` to `RabbitMqDemoApplication.java`**

Replace the entire file content with:

```java
package com.testingai.rabbitmq;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@OpenAPIDefinition(info = @Info(
        title = "RabbitMQ Demo API",
        version = "1.0.0",
        description = "Learning project demonstrating RabbitMQ messaging patterns"
))
@SpringBootApplication
public class RabbitMqDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(RabbitMqDemoApplication.class, args);
    }
}
```

- [ ] **Step 3: Verify tests still pass**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test
```

Expected: `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add message-brokers/rabbitmq/spring-demo/pom.xml \
        message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/RabbitMqDemoApplication.java
git commit -m "feat: add springdoc dependency and OpenAPI metadata"
```

---

### Task 2: Annotate DemoController

**Files:**
- Modify: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/controller/DemoController.java`

- [ ] **Step 1: Replace `DemoController.java` with the fully annotated version**

```java
package com.testingai.rabbitmq.controller;

import com.testingai.rabbitmq.pubsub.PubSubProducer;
import com.testingai.rabbitmq.routing.RoutingProducer;
import com.testingai.rabbitmq.simple.SimpleProducer;
import com.testingai.rabbitmq.workqueue.WorkQueueProducer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
@Tag(name = "RabbitMQ Demo", description = "Triggers for the four RabbitMQ messaging patterns")
public class DemoController {

    private final SimpleProducer simpleProducer;
    private final WorkQueueProducer workQueueProducer;
    private final PubSubProducer pubSubProducer;
    private final RoutingProducer routingProducer;

    @PostMapping("/simple")
    @Operation(summary = "Send to simple queue")
    public ResponseEntity<String> simple(
            @Parameter(description = "Text to send to the simple queue")
            @RequestParam String message) {
        simpleProducer.send(message);
        return ResponseEntity.ok("Sent to simple.queue: " + message);
    }

    @PostMapping("/work")
    @Operation(summary = "Send to work queue")
    public ResponseEntity<String> work(
            @Parameter(description = "Text to send; add dots to simulate work duration (e.g. task..)")
            @RequestParam String message,
            @Parameter(description = "Number of messages to dispatch (default 5)")
            @RequestParam(defaultValue = "5") int count) {
        workQueueProducer.send(message, count);
        return ResponseEntity.ok("Sent " + count + " messages to work.queue");
    }

    @PostMapping("/pubsub")
    @Operation(summary = "Broadcast via fanout exchange")
    public ResponseEntity<String> pubsub(
            @Parameter(description = "Text to broadcast to all fanout subscribers")
            @RequestParam String message) {
        pubSubProducer.send(message);
        return ResponseEntity.ok("Broadcast to pubsub.fanout: " + message);
    }

    @PostMapping("/routing")
    @Operation(summary = "Route via direct exchange")
    public ResponseEntity<String> routing(
            @Parameter(description = "Routing key — one of: info, warning, error",
                       schema = @Schema(allowableValues = {"info", "warning", "error"}))
            @RequestParam String key,
            @Parameter(description = "Text to route")
            @RequestParam String message) {
        routingProducer.send(key, message);
        return ResponseEntity.ok("Routed to routing.direct with key=" + key + ": " + message);
    }
}
```

- [ ] **Step 2: Verify all tests still pass**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test
```

Expected: `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS.

Note: `@WebMvcTest` in `DemoControllerTest` does not load springdoc beans, so all 5 controller tests continue to pass unchanged.

- [ ] **Step 3: Commit**

```bash
git add message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/controller/DemoController.java
git commit -m "feat: add OpenAPI annotations to DemoController"
```
