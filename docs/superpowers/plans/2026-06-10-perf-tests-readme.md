# Performance Tests & README Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Gatling performance tests for all four REST endpoints and a README documenting how to run the RabbitMQ demo project end-to-end.

**Architecture:** Two independent additions: (1) Gatling 3.13.1 Java DSL simulation in the existing Maven project — wired via `gatling-maven-plugin:4.15.0`, runs with `mvn gatling:test` separate from the unit test suite; (2) a Markdown README at `message-brokers/rabbitmq/README.md` covering cluster startup, app startup, all curl examples, Swagger UI, and performance test execution. Gatling simulations live in `src/test/java` alongside unit tests (they are excluded from `mvn test` by naming convention — surefire only picks up `*Test*.java`).

**Tech Stack:** Gatling 3.13.1 Java DSL (`io.gatling.highcharts:gatling-charts-highcharts`), `io.gatling:gatling-maven-plugin:4.15.0`, Java 21, Maven 3.9+

---

## File Map

- Modify: `message-brokers/rabbitmq/spring-demo/pom.xml` — add Gatling dependency + plugin
- Create: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/performance/DemoSimulation.java` — Gatling simulation
- Create: `message-brokers/rabbitmq/README.md` — project README

---

### Task 1: Add Gatling to pom.xml

**Files:**
- Modify: `message-brokers/rabbitmq/spring-demo/pom.xml`

- [ ] **Step 1: Add Gatling dependency inside `<dependencies>`**

In `message-brokers/rabbitmq/spring-demo/pom.xml`, add after the springdoc dependency (after line containing `</dependency>` that closes the springdoc block), before `</dependencies>`:

```xml
        <dependency>
            <groupId>io.gatling.highcharts</groupId>
            <artifactId>gatling-charts-highcharts</artifactId>
            <version>3.13.1</version>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Add gatling-maven-plugin inside `<plugins>`**

In `message-brokers/rabbitmq/spring-demo/pom.xml`, add after the `spring-boot-maven-plugin` closing `</plugin>` tag, before `</plugins>`:

```xml
            <plugin>
                <groupId>io.gatling</groupId>
                <artifactId>gatling-maven-plugin</artifactId>
                <version>4.15.0</version>
            </plugin>
```

- [ ] **Step 3: Verify existing unit tests still pass**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test
```

Expected: `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add message-brokers/rabbitmq/spring-demo/pom.xml
git commit -m "feat: add Gatling dependency and plugin for performance tests"
```

---

### Task 2: Create DemoSimulation.java

**Files:**
- Create: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/performance/DemoSimulation.java`

Note: Gatling simulations require a live server to execute. This task verifies the file compiles; actual execution instructions are in the README (Task 3) and in the spec prerequisites. `mvn test` will not pick up `DemoSimulation.java` — surefire's default include patterns are `**/*Test.java`, `**/*Tests.java`, `**/*TestCase.java`. The class name `DemoSimulation` does not match, so it stays out of the unit test run.

- [ ] **Step 1: Create the simulation file**

Create `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/performance/DemoSimulation.java` with this exact content:

```java
package com.testingai.rabbitmq.performance;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class DemoSimulation extends Simulation {

    HttpProtocolBuilder httpProtocol = http
            .baseUrl("http://localhost:8080");

    ScenarioBuilder simpleScenario = scenario("Simple Queue")
            .exec(http("POST /demo/simple")
                    .post("/demo/simple")
                    .queryParam("message", "perf-test")
                    .check(status().is(200)));

    ScenarioBuilder workScenario = scenario("Work Queue")
            .exec(http("POST /demo/work")
                    .post("/demo/work")
                    .queryParam("message", "task..")
                    .queryParam("count", "3")
                    .check(status().is(200)));

    ScenarioBuilder pubsubScenario = scenario("PubSub")
            .exec(http("POST /demo/pubsub")
                    .post("/demo/pubsub")
                    .queryParam("message", "perf-broadcast")
                    .check(status().is(200)));

    ScenarioBuilder routingScenario = scenario("Routing")
            .exec(http("POST /demo/routing")
                    .post("/demo/routing")
                    .queryParam("key", "info")
                    .queryParam("message", "perf-route")
                    .check(status().is(200)));

    {
        setUp(
                simpleScenario.injectOpen(
                        rampUsersPerSec(1).to(10).during(Duration.ofSeconds(30)),
                        constantUsersPerSec(10).during(Duration.ofSeconds(30))
                ),
                workScenario.injectOpen(
                        rampUsersPerSec(1).to(10).during(Duration.ofSeconds(30)),
                        constantUsersPerSec(10).during(Duration.ofSeconds(30))
                ),
                pubsubScenario.injectOpen(
                        rampUsersPerSec(1).to(10).during(Duration.ofSeconds(30)),
                        constantUsersPerSec(10).during(Duration.ofSeconds(30))
                ),
                routingScenario.injectOpen(
                        rampUsersPerSec(1).to(10).during(Duration.ofSeconds(30)),
                        constantUsersPerSec(10).during(Duration.ofSeconds(30))
                )
        ).protocols(httpProtocol)
                .assertions(
                        global().responseTime().percentile(95).lt(500),
                        global().failedRequests().percent().lt(1.0)
                );
    }
}
```

- [ ] **Step 2: Verify the simulation compiles**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test-compile
```

Expected: `BUILD SUCCESS` with no compilation errors.

- [ ] **Step 3: Verify existing unit tests are unaffected**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test
```

Expected: `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS. DemoSimulation must NOT appear in this output.

- [ ] **Step 4: Commit**

```bash
git add message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/performance/DemoSimulation.java
git commit -m "feat: add Gatling performance simulation for all four endpoints"
```

---

### Task 3: Create README.md

**Files:**
- Create: `message-brokers/rabbitmq/README.md`

- [ ] **Step 1: Create the README file**

Create `message-brokers/rabbitmq/README.md` with this exact content:

````markdown
# RabbitMQ Demo

A 3-node RabbitMQ Docker cluster and a Spring Boot demo app demonstrating four messaging patterns: simple queue, work queues, pub/sub (fanout exchange), and routing (direct exchange).

## Prerequisites

- Java 21
- Maven 3.9+
- Docker

## Start the cluster

```bash
cd docker
docker compose up -d
```

Wait ~30 seconds for the cluster to form, then verify:

```bash
docker exec rabbitmq1 rabbitmqctl cluster_status
```

Management UI: http://localhost:15672 (guest / guest)

## Run the app

```bash
cd spring-demo
mvn spring-boot:run
```

## Trigger endpoints

```bash
# Simple queue
curl -X POST "http://localhost:8080/demo/simple?message=hello"

# Work queue (dispatches 5 messages by default)
curl -X POST "http://localhost:8080/demo/work?message=task..&count=5"

# Pub/Sub — broadcast to all fanout subscribers
curl -X POST "http://localhost:8080/demo/pubsub?message=broadcast"

# Routing — direct exchange with routing key (info | warning | error)
curl -X POST "http://localhost:8080/demo/routing?key=error&message=boom"
```

## Swagger UI

http://localhost:8080/swagger-ui.html

## Run performance tests

Requires the cluster and app to be running (see above).

```bash
cd spring-demo
mvn gatling:test
```

HTML report: `target/gatling/<timestamp>/index.html`

## Stop the cluster

```bash
cd docker
docker compose down
```
````

- [ ] **Step 2: Verify the file exists**

```bash
ls -la message-brokers/rabbitmq/README.md
```

Expected: file listed with non-zero size.

- [ ] **Step 3: Commit**

```bash
git add message-brokers/rabbitmq/README.md
git commit -m "docs: add rabbitmq README with cluster, app, curl, and perf test instructions"
```
