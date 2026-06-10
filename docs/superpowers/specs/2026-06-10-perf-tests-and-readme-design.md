# Performance Tests & README — Design Spec

**Date:** 2026-06-10
**Scope:** `message-brokers/rabbitmq/`

---

## Goal

Two additions to the RabbitMQ demo:
1. Gatling performance tests covering all four REST endpoints
2. A `README.md` at `message-brokers/rabbitmq/` documenting how to run the whole project

---

## Part 1: Performance Tests (Gatling)

### Tool

Gatling 3.13.1 with Java DSL. Integrates with Maven via `gatling-maven-plugin:4.15.0`. Runs independently of unit tests (`mvn gatling:test` vs `mvn test`). Generates an HTML report in `target/gatling/`.

### Prerequisites

The Spring Boot app must be running (`mvn spring-boot:run` from `spring-demo/`) and the RabbitMQ Docker cluster must be up before running performance tests.

### pom.xml changes

Add test dependency:
```xml
<dependency>
    <groupId>io.gatling.highcharts</groupId>
    <artifactId>gatling-charts-highcharts</artifactId>
    <version>3.13.1</version>
    <scope>test</scope>
</dependency>
```

Add build plugin:
```xml
<plugin>
    <groupId>io.gatling</groupId>
    <artifactId>gatling-maven-plugin</artifactId>
    <version>4.15.0</version>
</plugin>
```

### Simulation file

`src/test/java/com/testingai/rabbitmq/performance/DemoSimulation.java`

- Extends `io.gatling.javaapi.core.Simulation`
- Base URL: `http://localhost:8080`
- 4 scenarios (one per endpoint):

| Scenario | HTTP call |
|---|---|
| Simple queue | `POST /demo/simple?message=perf-test` |
| Work queue | `POST /demo/work?message=task..&count=3` |
| Pub/Sub | `POST /demo/pubsub?message=perf-broadcast` |
| Routing | `POST /demo/routing?key=info&message=perf-route` |

- Load profile (same for all 4): ramp from 1 to 10 users over 30 seconds, then hold for 30 seconds
- Assertions: 95th percentile response time < 500 ms AND global error rate < 1%

### Run command

```bash
cd message-brokers/rabbitmq/spring-demo
mvn gatling:test
```

HTML report: `target/gatling/<timestamp>/index.html`

---

## Part 2: README

**Location:** `message-brokers/rabbitmq/README.md`

### Sections

1. **Overview** — folder contains a 3-node RabbitMQ Docker cluster and a Spring Boot demo app demonstrating four messaging patterns: simple queue, work queues, pub/sub, and routing via direct exchange

2. **Prerequisites** — Java 21, Maven 3.9+, Docker

3. **Start the cluster**
   ```bash
   cd docker
   docker compose up -d
   # wait ~30s, then verify:
   docker exec rabbitmq1 rabbitmqctl cluster_status
   ```
   Management UI: `http://localhost:15672` (guest/guest)

4. **Run the Spring Boot app**
   ```bash
   cd spring-demo
   mvn spring-boot:run
   ```

5. **Trigger endpoints** — `curl` examples for all 4 POST endpoints:
   - `curl -X POST "http://localhost:8080/demo/simple?message=hello"`
   - `curl -X POST "http://localhost:8080/demo/work?message=task..&count=5"`
   - `curl -X POST "http://localhost:8080/demo/pubsub?message=broadcast"`
   - `curl -X POST "http://localhost:8080/demo/routing?key=error&message=boom"`

6. **Swagger UI** — `http://localhost:8080/swagger-ui.html`

7. **Run performance tests**
   ```bash
   cd spring-demo
   mvn gatling:test
   # report: target/gatling/<timestamp>/index.html
   ```

8. **Stop the cluster**
   ```bash
   cd docker
   docker compose down
   ```
