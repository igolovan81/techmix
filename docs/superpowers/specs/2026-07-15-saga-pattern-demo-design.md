# Saga Pattern Demo Design

**Date:** 2026-07-15
**Status:** Approved

## Overview

A new top-level `distributed-transactions/` category (sibling to `message-brokers/`, `noSQL/`, `cqrs-event-sourcing/`, `template-engines/`), containing a single Spring Boot demo app — `saga/spring-demo` — that demonstrates the Saga pattern for distributed transactions via **both** classic implementation styles side by side: **choreography** (event-driven, no coordinator) and **orchestration** (a central orchestrator drives each step and its compensations). Both flows model the same e-commerce order-checkout scenario: reserve inventory → charge payment → arrange shipping, with compensations (release inventory, refund payment, cancel order) on failure.

No external infrastructure is required — choreography uses in-process Spring application events (`ApplicationEventPublisher`/`@EventListener`), not a message broker, so the whole module is self-contained like `template-engines/`. Step failures (which trigger compensation) are triggered deterministically via an explicit `failAt` field on the checkout request rather than random failure injection, so the compensation cascade can be demonstrated reliably in a live walkthrough.

## Repository structure

```
distributed-transactions/
├── pom.xml                                          (new parent POM, mirrors template-engines/pom.xml)
├── eclipse-formatter.xml                            (copy — same style repo-wide)
├── README.md                                        (category overview: choreography vs orchestration comparison table)
└── saga/
    └── spring-demo/
        ├── pom.xml                                  (artifactId: saga-demo)
        ├── README.md
        └── src/
            ├── main/
            │   ├── java/com/testingai/saga/
            │   │   ├── SagaDemoApplication.java
            │   │   ├── domain/
            │   │   │   ├── SagaStep.java                     (enum: RESERVE_INVENTORY, PROCESS_PAYMENT, ARRANGE_SHIPPING)
            │   │   │   ├── SagaStatus.java                   (enum: PENDING, CONFIRMED, CANCELLED)
            │   │   │   ├── OrderLine.java                    (record: productId, quantity, unitPrice)
            │   │   │   └── CheckoutRequest.java               (record: customerId, List<OrderLine> items, SagaStep failAt — nullable)
            │   │   ├── choreography/
            │   │   │   ├── event/                             (OrderCreated, InventoryReserved, InventoryReservationFailed,
            │   │   │   │                                        PaymentProcessed, PaymentFailed, PaymentRefunded,
            │   │   │   │                                        ShipmentArranged, ShipmentFailed,
            │   │   │   │                                        InventoryReleased, OrderConfirmed, OrderCancelled — records)
            │   │   │   ├── OrderParticipant.java              (creates/confirms/cancels the order; listens for CheckoutRequested,
            │   │   │   │                                        ShipmentArranged, InventoryReservationFailed, InventoryReleased)
            │   │   │   ├── InventoryParticipant.java          (reserves/releases stock; listens for OrderCreated, PaymentFailed, PaymentRefunded)
            │   │   │   ├── PaymentParticipant.java            (charges/refunds; listens for InventoryReserved, ShipmentFailed)
            │   │   │   ├── ShippingParticipant.java           (arranges shipment; listens for PaymentProcessed)
            │   │   │   ├── SagaLog.java                       (in-memory ConcurrentHashMap<orderId, List<SagaLogEntry>>, appended to by every participant)
            │   │   │   └── SagaLogEntry.java                  (record: step, outcome (enum: SUCCEEDED, FAILED, COMPENSATED), detail (String, e.g. failure reason), timestamp)
            │   │   ├── orchestration/
            │   │   │   ├── StepOutcome.java                   (sealed interface: Success(SagaStep), Failure(SagaStep, String reason))
            │   │   │   ├── SagaResult.java                    (record: orderId, SagaStatus, failedStep (nullable), compensatedSteps)
            │   │   │   ├── SagaOrchestrator.java              (drives the three participants below in order; on Failure, compensates
            │   │   │   │                                        completed steps in reverse)
            │   │   │   ├── InventoryParticipant.java          (reserve(orderId, items, failAt) / compensate(orderId))
            │   │   │   ├── PaymentParticipant.java            (charge(orderId, items, failAt) / compensate(orderId))
            │   │   │   └── ShippingParticipant.java           (arrange(orderId, failAt) / compensate(orderId))
            │   │   └── controller/
            │   │       ├── DemoController.java                (all endpoints below)
            │   │       ├── CheckoutResponse.java               (record: orderId — choreography's 202 response)
            │   │       └── DemoExceptionHandler.java
            │   └── resources/
            │       └── application.yml                        (server.port: 8089)
            └── test/
                ├── java/com/testingai/saga/
                │   ├── SagaDemoApplicationTest.java
                │   ├── choreography/
                │   │   ├── OrderParticipantTest.java
                │   │   ├── InventoryParticipantTest.java
                │   │   ├── PaymentParticipantTest.java
                │   │   └── ShippingParticipantTest.java
                │   ├── orchestration/
                │   │   └── SagaOrchestratorTest.java
                │   ├── controller/
                │   │   └── SagaIntegrationTest.java            (MockMvc, both flows, happy path + failure-at-each-step)
                │   └── performance/
                │       └── SagaSimulation.java
                └── resources/application.yml
```

### Cross-cutting fixes needed in existing files

- **`.githooks/pre-commit`** — extend the staged-file grep from `^(message-brokers|noSQL|cqrs-event-sourcing|template-engines)/.*\.java$` to also match `^distributed-transactions/.*\.java$`, and add a matching `mvn spotless:apply` block run from `distributed-transactions/`.
- **`CLAUDE.md`** — add a "Saga pattern demo" command section (mirroring the CQRS/Event Sourcing section), a `distributed-transactions/` row in the repository layout table, and a line noting no infrastructure/docker is required for this category.

## Domain model

```java
public enum SagaStep { RESERVE_INVENTORY, PROCESS_PAYMENT, ARRANGE_SHIPPING }
public enum SagaStatus { PENDING, CONFIRMED, CANCELLED }
public record OrderLine(String productId, int quantity, BigDecimal unitPrice) {}
public record CheckoutRequest(String customerId, List<OrderLine> items, SagaStep failAt) {}
```

`failAt` is nullable — `null` means the happy path; a non-null value tells the participant owning that step to fail deterministically instead of succeeding, which starts the compensation cascade.

Business logic (fake stock check, fake charge, fake shipment booking) is **not shared** between the two flows — each flow has its own trio of participant classes. Each is a handful of lines; duplicating them keeps `choreography/` and `orchestration/` fully self-contained and readable independently, rather than hiding the actual work behind a shared service layer that would blur which flow is doing what.

**Step order:** reserve inventory → charge payment → arrange shipping. Reserving stock before charging avoids charging a customer for an item that turns out to be out of stock — called out explicitly in the module README.

## Choreography flow (`com.testingai.saga.choreography`)

Participants communicate only through `ApplicationEventPublisher` / `@EventListener` — no participant calls another directly, and there is no coordinator. Spring's default event publishing is **synchronous** (same thread), which is a deliberate choice here (not `@Async`): the checkout endpoint returns the final, settled result in one call, and tests stay deterministic with no polling. The pattern's teaching point is the *decoupled structure* — each participant only knows the event immediately before and after it — not async timing. The module README calls out that a real cross-service deployment would put a broker (e.g. Kafka, as in `message-brokers/kafka`) between these steps for genuine cross-process async decoupling; this demo keeps that same event-driven shape in a single process for simplicity.

**Happy-path event chain:**

```
CheckoutRequested (from controller)
  → OrderCreated                (OrderParticipant)
  → InventoryReserved           (InventoryParticipant)
  → PaymentProcessed            (PaymentParticipant)
  → ShipmentArranged            (ShippingParticipant)
  → OrderConfirmed              (OrderParticipant)
```

**Failure/compensation chains** — each failure event cascades *backward* one participant at a time, so no participant needs global saga knowledge:

- `failAt = RESERVE_INVENTORY`: `OrderCreated → InventoryReservationFailed → OrderCancelled` (nothing to compensate yet).
- `failAt = PROCESS_PAYMENT`: `InventoryReserved → PaymentFailed → InventoryReleased → OrderCancelled`.
- `failAt = ARRANGE_SHIPPING`: `PaymentProcessed → ShipmentFailed → PaymentRefunded → InventoryReleased → OrderCancelled`.

Every event carries `orderId`. `SagaLog` is populated by every participant listener (both success and failure/compensation entries) and is the only way to inspect what happened, queried via `GET /demo/saga/choreography/orders/{orderId}`.

## Orchestration flow (`com.testingai.saga.orchestration`)

A single `SagaOrchestrator` component drives its own `InventoryParticipant`, `PaymentParticipant`, `ShippingParticipant` (separate classes from the choreography ones — same fake-logic shape, plain methods instead of event listeners) by calling them directly in sequence. No events.

```java
sealed interface StepOutcome permits StepOutcome.Success, StepOutcome.Failure {
    record Success(SagaStep step) implements StepOutcome {}
    record Failure(SagaStep step, String reason) implements StepOutcome {}
}
```

The orchestrator keeps a `List<SagaStep> completedSteps` as it advances. On a `Failure`, it walks `completedSteps` **in reverse**, calling each participant's `compensate(orderId)`, then returns a `SagaResult` record (final `SagaStatus`, the failed step if any, and which steps were compensated). Fully synchronous end-to-end: the controller call blocks until the whole saga — including any compensation — is finished, returning one authoritative result object. That's the direct contrast with choreography's separately-queried timeline.

## API surface

Single `DemoController`, consistent with every other module in the repo:

| Endpoint | Behavior |
|---|---|
| `POST /demo/saga/choreography/checkout` | Body: `CheckoutRequest`. Publishes `CheckoutRequested`; cascade runs synchronously; returns `202 Accepted` with `CheckoutResponse{orderId}` once settled. |
| `GET /demo/saga/choreography/orders/{orderId}` | Returns the `SagaLog` timeline (ordered `SagaLogEntry` list) and final `SagaStatus`. `404` if unknown. |
| `POST /demo/saga/orchestration/checkout` | Body: `CheckoutRequest`. Runs `SagaOrchestrator` synchronously; returns `200 OK` with the full `SagaResult`. |

Swagger UI at `/swagger-ui/index.html`. `DemoExceptionHandler` maps unexpected errors to a clean error response, matching the axon module's convention.

## Testing

- Unit tests per choreography participant (each listener's success and failure/compensation behavior) and for `SagaOrchestrator`'s step-execution and reverse-compensation-ordering logic.
- `SagaIntegrationTest` — `@SpringBootTest` + `MockMvc` (plain JUnit5/Spring, no Spock, so `@WebMvcTest`/`@SpringBootTest` applies cleanly per [[spock-spring-webmvctest-incompatibility]]), covering: choreography happy path, choreography failure at each of the three steps (asserting the correct compensation cascade via the `SagaLog` endpoint), orchestration happy path, orchestration failure at each step (asserting `SagaResult.compensatedSteps`).
- `src/test/.../performance/SagaSimulation.java` — Gatling load test hitting both checkout endpoints with a mix of happy-path and forced-failure (`failAt` set) requests; excluded from `mvn test` via the inherited surefire `**/performance/**` exclude, run explicitly via `mvn gatling:test`.
- No `util/FailureSimulator` — failures are deterministic via `CheckoutRequest.failAt`, not randomized, so the shared `FailureSimulator` convention (`FAILURE_RATE`/`maybeThrow`) used in `message-brokers/` does not apply here.

## Ports

- `saga/spring-demo` → `8089` (next free slot after `template-engines/freemarker`'s `8088`).

## Spring Boot configuration

**Spring Boot version:** 3.4.x
**Java:** 21

**`saga-demo` dependencies:** `spring-boot-starter-web`, `springdoc-openapi-starter-webmvc-ui`, `lombok`, `spring-boot-starter-test` (test), `gatling-charts-highcharts` (test). No messaging, no persistence, no database driver — everything is in-memory.

## README

`distributed-transactions/saga/spring-demo/README.md` follows the axon module's format: prerequisites (Java 21, Maven — no Docker needed), run instructions (`mvn spring-boot:run`), an architecture diagram per flow, a patterns-demonstrated table, and full `curl` walkthroughs for both flows showing the happy path and a forced failure on each of the three steps (making the compensation cascade concrete, e.g. showing the `SagaLog` before/after a `PROCESS_PAYMENT` failure). Swagger UI link and Gatling instructions included.

`distributed-transactions/README.md` is a short category index (analogous to `cqrs-event-sourcing/README.md`), comparing choreography vs. orchestration (coupling, coordination visibility, best fit) and ready to grow if more saga implementation styles or frameworks are added later (e.g. an Axon Framework Saga demo alongside the existing `cqrs-event-sourcing/axon` module).

## Scope limits

- No message broker / external infrastructure — choreography uses in-process Spring events, not Kafka/RabbitMQ; this is a deliberate simplification called out in the README, not an oversight.
- No persistence layer — both flows are pure in-memory simulations of order/inventory/payment/shipping; nothing is durable across app restarts, unlike a production saga which would need persisted saga/order state to survive a crash mid-flight. That durability concern is out of scope here.
- No random `FailureSimulator`-style failure injection — failures are explicit and deterministic via `failAt`, for reliable demoing.
- No retry logic on individual steps (only compensation) — retries-before-compensation is a legitimate real-world addition but would blur the core choreography/orchestration comparison this demo is built to show.
- No Axon Framework Saga support, even though `cqrs-event-sourcing/axon` already exists in this repo — deliberately kept separate so this module teaches the pattern's fundamentals framework-free; an Axon-based saga demo is a natural future addition to `cqrs-event-sourcing/`, not this module.
