# Camunda Workflow Engine Demo Design

**Date:** 2026-07-29
**Status:** Approved

## Overview

A new top-level `workflow-engines/` category (sibling to `message-brokers/`, `noSQL/`, `cqrs-event-sourcing/`, `template-engines/`, `distributed-transactions/`, `communication-protocols/`), containing a `camunda/` module with a **single** Spring Boot app (`spring-demo`) built on Camunda 8 (Zeebe) via `io.camunda:camunda-spring-boot-starter`.

The domain is the same order-fulfillment flow already used by `distributed-transactions/saga` (reserve inventory → process payment → arrange shipping), specifically so the two demos are directly comparable: saga hand-writes orchestration and compensation in Java; this demo expresses the same process as a BPMN diagram, with Camunda's engine driving execution, branching, human tasks, and failure routing declaratively. It reuses saga's `failAt`-driven deterministic-failure convention (not `FailureSimulator`) for the same reason saga does — a demo needs to reliably trigger its failure paths on command, not 5% of the time.

No SaaS dependency — a local Camunda 8 self-managed cluster (`camunda/camunda` unified Docker image + Elasticsearch) runs via `docker compose`. Automated tests do **not** need that compose stack running: Camunda 8.8+ ships an official Testcontainers-backed test library (`camunda-process-test-spring`, `@CamundaSpringProcessTest`) that spins up a real, ephemeral Camunda runtime per test class. This is the one module in the repo where `mvn test` needs a working Docker daemon — unavoidable given how Camunda 8 testing works — called out explicitly as a scope note.

## Repository structure

```
workflow-engines/
├── pom.xml                                          (new parent POM, mirrors cqrs-event-sourcing/pom.xml)
├── eclipse-formatter.xml                            (copy — same style repo-wide)
├── README.md                                        (category overview)
└── camunda/
    ├── README.md                                    (protocol overview: BPMN/Camunda 8, the 4-pattern table, docker instructions, Operate walkthrough callout)
    ├── docker/
    │   └── docker-compose.yml                       (camunda/camunda unified image [Zeebe + Operate + Tasklist + REST/gRPC gateway] + elasticsearch)
    └── spring-demo/
        ├── pom.xml                                  (artifactId: camunda-demo)
        ├── README.md                                (curl walkthroughs for all four patterns, build/test commands, Testcontainers scope note)
        └── src/
            ├── main/
            │   ├── java/com/testingai/camunda/
            │   │   ├── CamundaDemoApplication.java
            │   │   ├── domain/
            │   │   │   ├── OrderLine.java             (record: productId, quantity, unitPrice — same shape as saga's OrderLine)
            │   │   │   ├── CheckoutRequest.java        (record: customerId, items, failAt)
            │   │   │   ├── OrderStep.java               (enum: RESERVE_INVENTORY, PROCESS_PAYMENT, ARRANGE_SHIPPING — mirrors saga's SagaStep)
            │   │   │   ├── OrderStatus.java             (enum: PENDING_APPROVAL, IN_PROGRESS, FULFILLED, CANCELLED)
            │   │   │   └── OrderReadModel.java          (in-memory Map<orderId, OrderView> updated by workers/controller — no persistence layer)
            │   │   ├── worker/
            │   │   │   ├── InventoryWorker.java        (@JobWorker "reserve-inventory" and "release-inventory")
            │   │   │   ├── PaymentWorker.java           (@JobWorker "process-payment")
            │   │   │   └── ShippingWorker.java          (@JobWorker "arrange-shipping")
            │   │   └── controller/
            │   │       ├── DemoController.java         (start process, approve/reject user task, get order status)
            │   │       └── DemoExceptionHandler.java
            │   └── resources/
            │       ├── application.yml                  (server.port: 8093; camunda.client.mode: self-managed, grpc-address/rest-address)
            │       └── bpmn/
            │           └── order-fulfillment.bpmn        (see Process shape below; authored as valid BPMN 2.0 XML during implementation)
            └── test/
                └── java/com/testingai/camunda/
                    ├── CamundaDemoApplicationTest.java   (@CamundaSpringProcessTest context loads)
                    ├── worker/
                    │   ├── InventoryWorkerTest.java       (mocked ActivatedJob/JobClient; happy path + failAt-triggered ZeebeBpmnError)
                    │   ├── PaymentWorkerTest.java
                    │   └── ShippingWorkerTest.java
                    ├── controller/
                    │   └── DemoIntegrationTest.java       (@SpringBootTest + @CamundaSpringProcessTest, Testcontainers-backed; see Testing below)
                    └── performance/
                        └── DemoSimulation.java            (Gatling; drives the REST surface for the low-value happy path only)
```

### Cross-cutting fixes needed in existing files

- **`.githooks/pre-commit`** — extend the staged-file grep to also match `^workflow-engines/.*\.java$`, and add a matching `mvn spotless:apply` block run from `workflow-engines/`, following the exact pattern already used for `communication-protocols/`.
- **`CLAUDE.md`** — add a "Camunda workflow engine demo" command section (needs `docker compose up -d` first, unlike the docker-free communication-protocols demos) and a `workflow-engines/camunda/spring-demo/` row in the repository layout table.

## Domain model

Reused/adapted from `distributed-transactions/saga` for direct comparability:

```java
public record OrderLine(String productId, int quantity, BigDecimal unitPrice) {}

public record CheckoutRequest(String customerId, List<OrderLine> items, OrderStep failAt) {}

public enum OrderStep { RESERVE_INVENTORY, PROCESS_PAYMENT, ARRANGE_SHIPPING }

public enum OrderStatus { PENDING_APPROVAL, IN_PROGRESS, FULFILLED, CANCELLED }
```

`failAt` (nullable) drives deterministic failure injection into the matching job worker, exactly like saga's `SagaStep failAt` — chosen specifically so this demo's failure/error-boundary paths are exercised on command in tests and walkthroughs, not at a random 5% rate.

## Process shape (`order-fulfillment.bpmn`)

```
[Start: Order Placed]
        │
        ▼
[Service Task: Reserve Inventory] ──(error boundary: INVENTORY_UNAVAILABLE)──▶ [Release Inventory*] ──▶ [End: Order Cancelled]
        │
        ▼
   ⬦ High-Value Order? (totalCents > 50000, i.e. $500 — a FEEL expression on the outgoing sequence flow)
        │yes                              │no
        ▼                                 │
[User Task: Approve Order]                │
        │approved      │rejected          │
        │               └──▶ [Release Inventory*] ──▶ [End: Order Cancelled]
        ▼                                 │
        └────────────────◀────────────────┘
        ▼
[Service Task: Process Payment] ──(error boundary: PAYMENT_DECLINED)──▶ [Release Inventory*] ──▶ [End: Order Cancelled]
        │
        ▼
[Service Task: Arrange Shipping]
        │
        ▼
[End: Order Fulfilled]
```

`*Release Inventory` is one shared cleanup service task (job type `release-inventory`, implemented in `InventoryWorker` alongside `reserve-inventory`), reached from all three failure/rejection paths.

**Deliberate simplification:** error boundary events (not full BPMN Compensation Events with throw-compensation + `isForCompensation` associations) drive the cleanup path. This teaches "declarative error routing vs. saga's hand-written `compensate()` step-unwinding loop" without the added modeling ceremony of native compensation semantics. Called out as a scope limit in the module README, the same way saga's own README documents its scope limits.

Process variables carried through the instance: `orderId`, `customerId`, `items` (JSON array), `totalCents`, `failAt` (nullable string), `inventoryReserved` (boolean), `approved` (boolean, set by the user task completion), `status` (`OrderStatus` as string, kept in sync with the in-memory read model).

## Patterns implemented (parallels gRPC's/GraphQL's pattern tables)

| Pattern | BPMN element | What it demonstrates |
|---|---|---|
| Service tasks | `Reserve Inventory`, `Process Payment`, `Arrange Shipping`, `Release Inventory` | External worker processes (`@JobWorker`) polling/executing units of work — Camunda's equivalent of saga's hand-written participants, but declared in the process model rather than Java control flow |
| Exclusive gateway | `High-Value Order?` | Conditional branching expressed declaratively in the process diagram (a FEEL expression on the sequence flow) instead of an `if` statement buried in orchestrator code |
| User task | `Approve Order` | Human-in-the-loop step — something saga's code-only orchestrator has no first-class way to express; the process instance genuinely pauses until a human (or, in this demo, a REST call) completes it |
| Error boundary event | attached to `Reserve Inventory` / `Process Payment` | Declarative failure routing to a cleanup path, BPMN's alternative to saga's manually-written `compensate()` step-unwinding loop |

## Workers

- **`InventoryWorker`** — `@JobWorker(type = "reserve-inventory")`: marks `inventoryReserved=true` in the read model; throws `ZeebeBpmnError("INVENTORY_UNAVAILABLE", ...)` when `failAt == RESERVE_INVENTORY`. Also owns `@JobWorker(type = "release-inventory")` — the cleanup task, undoing the reservation — living next to the task it undoes, mirroring saga's `InventoryParticipant.compensate()`.
- **`PaymentWorker`** — `@JobWorker(type = "process-payment")`: throws `ZeebeBpmnError("PAYMENT_DECLINED", ...)` when `failAt == PROCESS_PAYMENT`.
- **`ShippingWorker`** — `@JobWorker(type = "arrange-shipping")`: marks the order `FULFILLED` in the read model.

Each worker updates `OrderReadModel` as a side effect, which `DemoController`'s `GET` endpoint reads from (no separate persistence layer, matching `template-engines`/`saga`'s in-memory conventions).

## `DemoController`

| Endpoint | Behavior |
|---|---|
| `POST /demo/camunda/orders` | Body: `CheckoutRequest`. Starts a process instance (`CamundaClient.newCreateInstanceCommand()...variables(...)`), registers the order in `OrderReadModel` as `PENDING_APPROVAL`/`IN_PROGRESS` depending on order value, returns `{ orderId, processInstanceKey }` |
| `POST /demo/camunda/orders/{orderId}/approval` | Body: `{ approved: boolean }`. Completes the `Approve Order` user task for that order's process instance with the `approved` variable |
| `GET /demo/camunda/orders/{orderId}` | Returns the current `OrderStatus` + completed steps from `OrderReadModel` |

## Testing

- **Unit tests** — `InventoryWorkerTest`, `PaymentWorkerTest`, `ShippingWorkerTest`: call worker methods directly with a mocked `ActivatedJob`/`JobClient`, asserting the happy path and the `failAt`-triggered `ZeebeBpmnError`, same style as saga's `PaymentParticipantTest`.
- **Integration test** (`DemoIntegrationTest`) — `@SpringBootTest` + `@CamundaSpringProcessTest` (Testcontainers-backed; pulls and runs the `camunda/camunda` image per test class, no manually-running docker-compose needed):
  - happy path (low-value order): start → assert `Reserve Inventory`/`Process Payment`/`Arrange Shipping` complete in order, `Approve Order` never appears, process `isCompleted()`
  - high-value order: assert `Approve Order` `isCreated()`, complete it via `processTestContext.completeUserTask(...)` with `approved=true`, assert completion continues through payment/shipping
  - rejection path: complete the user task with `approved=false`, assert routing to `Release Inventory` → `Order Cancelled`
  - `failAt=RESERVE_INVENTORY` and `failAt=PROCESS_PAYMENT`: assert the matching error boundary path fires and `Release Inventory` runs
- **`performance/DemoSimulation.java`** (Gatling) — drives `POST /demo/camunda/orders` for the low-value happy-path flow only; excluded from `mvn test` via the inherited surefire `**/performance/**` exclude, run explicitly via `mvn gatling:test` against the docker-compose-backed app. High-value/approval flows aren't load-tested since they need a human-shaped follow-up call mid-flight, not a fire-and-forget request. No JMeter test for this module — the load-testing story here is intentionally thin (one flow), so a second tool duplicating the same single scenario isn't worth the maintenance; this deviates from communication-protocols' dual Gatling+JMeter convention and is called out in the module README.

## Ports & infrastructure

- `spring-demo` → HTTP `8093` (next free slot after `8081`–`8092`, `8094`).
- `docker/docker-compose.yml` → `camunda/camunda` unified image (bundles Zeebe broker + REST gateway `:8080` + gRPC gateway `:26500` + Operate + Tasklist web UI) plus `elasticsearch` (secondary storage, required by Operate). Two containers, not three — Camunda 8.8+ consolidated Operate/Tasklist into the single `camunda` image rather than separate services.
- `application.yml`: `camunda.client.mode: self-managed`, `camunda.client.grpc-address: http://localhost:26500`, `camunda.client.rest-address: http://localhost:8080`.

## Spring Boot configuration

**Spring Boot version:** 3.4.4 (new parent POM mirrors the existing ones)
**Java:** 21

**Dependencies:** `io.camunda:camunda-spring-boot-starter:8.9.x`, `spring-boot-starter-web`, `lombok`, `spring-boot-starter-test` (test), `io.camunda:camunda-process-test-spring` (test, brings in Testcontainers), `gatling-charts-highcharts` (test).

## README

- `workflow-engines/README.md` — category index, mirrors `cqrs-event-sourcing/README.md`; room to grow (e.g. a future Temporal or Flowable demo).
- `workflow-engines/camunda/README.md` — protocol-level overview: what BPMN/Camunda 8 is in a paragraph, the 4-pattern table with pros/cons/use-cases (same shape as gRPC/GraphQL READMEs), `docker compose up -d` instructions, an Operate walkthrough callout (watch the process instance move through the diagram live — the Axon-dashboard-style hook already established by the Axon demo's README).
- `workflow-engines/camunda/spring-demo/README.md` — module walkthrough: prerequisites (Java 21, Maven, Docker), `curl` examples for all four patterns including the high-value-order approval flow and both failure paths, build/test commands, and the Testcontainers scope note.

## Scope limits

- Camunda 8 self-managed only (the `camunda/camunda` unified image + Elasticsearch) — no Camunda 8 SaaS, no Identity/auth service (single unauthenticated cluster), matching this repo's "local demo, not production hardening" scope of every other module.
- Error boundary events only, not full BPMN Compensation Events — see the process-shape note above.
- No Connectors, no DMN decision tables, no multi-instance/parallel gateways — one linear happy path plus the branches already described, to keep the diagram legible and the demo focused on the four patterns above.
- In-memory read model only (`OrderReadModel`) — no persistence layer, matching `template-engines`/`saga`'s in-memory conventions.
- `mvn test` requires a working Docker daemon (Testcontainers) — the one module in this repo where that's true, called out explicitly rather than silently deviating from the repo-wide "tests need no infra" norm.
- No JMeter load test — Gatling only, covering the single low-value happy-path flow; deviates from `communication-protocols`' dual-tool convention, called out in the module README.
