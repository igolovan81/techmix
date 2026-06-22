# Axon Framework CQRS/Event Sourcing Demo Design

**Date:** 2026-06-22
**Status:** Approved

## Overview

A new top-level `cqrs-event-sourcing/` category (sibling to `message-brokers/` and `noSQL/`), with Axon Server as its event store/router and a Spring Boot demo app, `axon`, as its first module. Demonstrates core CQRS/event-sourcing mechanics — commands, event-sourced aggregates, a separately-updated query model, event replay, and snapshotting — using an `Order` domain consistent with the one already established in `noSQL/mongodb`. Mirrors this repo's established demo-app conventions (REST-triggered patterns, Swagger UI, Spotless formatting, Gatling performance tests) so the codebase stays consistent as more CQRS/ES frameworks are potentially added later.

## Repository structure

```
cqrs-event-sourcing/
├── pom.xml                                          (new parent POM, mirrors noSQL/pom.xml)
├── eclipse-formatter.xml                            (copy — same style repo-wide)
├── README.md                                        (short index; lists the axon module)
└── axon/
    ├── docker/
    │   └── docker-compose.yml                       (Axon Server, single node)
    ├── spring-demo/
    │   ├── pom.xml                                  (artifactId: axon-demo)
    │   └── src/
    │       ├── main/
    │       │   ├── java/com/testingai/axon/
    │       │   │   ├── AxonDemoApplication.java
    │       │   │   ├── config/
    │       │   │   │   └── AxonConfig.java            (SnapshotTriggerDefinition bean)
    │       │   │   ├── controller/
    │       │   │   │   └── DemoController.java
    │       │   │   ├── command/
    │       │   │   │   ├── OrderAggregate.java         (@Aggregate, event-sourced)
    │       │   │   │   ├── CreateOrderCommand.java
    │       │   │   │   ├── AddOrderLineCommand.java
    │       │   │   │   ├── ConfirmOrderCommand.java
    │       │   │   │   └── CancelOrderCommand.java
    │       │   │   ├── event/
    │       │   │   │   ├── OrderCreatedEvent.java
    │       │   │   │   ├── OrderLineAddedEvent.java
    │       │   │   │   ├── OrderConfirmedEvent.java
    │       │   │   │   └── OrderCancelledEvent.java
    │       │   │   ├── query/
    │       │   │   │   ├── OrderSummary.java           (read-model record)
    │       │   │   │   ├── OrderProjection.java         (@EventHandler methods + in-memory repository)
    │       │   │   │   ├── FindOrderQuery.java
    │       │   │   │   └── FindAllOrdersQuery.java
    │       │   │   ├── replay/
    │       │   │   │   └── ReplayService.java           (resets the projection's TrackingEventProcessor)
    │       │   │   └── util/
    │       │   │       └── FailureSimulator.java         (FAILURE_RATE = 0.05, maybeThrow(String context))
    │       │   └── resources/
    │       │       └── application.yml
    │       └── test/
    │           ├── java/com/testingai/axon/
    │           │   ├── AxonDemoApplicationTest.java
    │           │   ├── command/OrderAggregateTest.java   (AggregateTestFixture)
    │           │   ├── query/OrderProjectionTest.java
    │           │   ├── controller/DemoControllerTest.java
    │           │   └── performance/DemoSimulation.java
    │           └── resources/application.yml
    └── README.md
```

### Cross-cutting fixes needed in existing files

- **`.githooks/pre-commit`** currently formats staged Java files only under `message-brokers/` and `noSQL/` (`grep -E '^(message-brokers|noSQL)/.*\.java$'`). Extend the pattern to also match `^cqrs-event-sourcing/.*\.java$` and run `mvn spotless:apply` from `cqrs-event-sourcing/` for those files.
- **`CLAUDE.md`** gets a new short section documenting the `cqrs-event-sourcing/` category and its commands (mirroring the existing `noSQL/` section), a new row in the repository layout table, and a new line in the infrastructure section for the Axon Server docker-compose file.

## Axon Server topology

A single-node Axon Server instance (no clustering — matches the demo's scope; Axon Server clustering is an enterprise-style concern, not something this demo needs to teach):

```
┌────────────────────┐
│    axonserver       │
│  gRPC :8124          (client connections — commands/events/queries)
│  HTTP :8024           (dashboard UI + REST admin API)
└────────────────────┘

Spring Boot app: 8086 (8085 is already used by Pulsar's admin HTTP host mapping; 8086 is the next free slot)
```

- `axon.axonserver.servers: localhost:8124` in `application.yml` — the Axon Spring Boot starter auto-configures `CommandGateway`, `QueryGateway`, and `EventGateway` beans against this.
- No authentication/TLS — local-dev simplicity, consistent with Kafka/Redis/Mongo's no-auth setup.
- Dashboard UI at `http://localhost:8024` lets you browse the event store per-aggregate, mirroring the role Kafka UI / mongo-express play in their modules.
- Healthcheck: `curl -f http://localhost:8024/actuator/health`.

## Domain model & demo patterns

Reuses the `Order` concept already established in `noSQL/mongodb` (familiar across modules), modeled here as a single event-sourced aggregate:

| Pattern | Components | What it demonstrates |
|---|---|---|
| **Command handling + event sourcing** | `command/`, `event/` | `OrderAggregate` handles commands by validating and applying events; its state is rehydrated purely by replaying past events (`@EventSourcingHandler`), never by direct field mutation |
| **CQRS query model** | `query/` | A separate, denormalized `OrderSummary` read model is updated asynchronously by `@EventHandler` methods reacting to the same events, and queried independently via `QueryGateway` — proves the read and write models are decoupled |
| **Replay** | `replay/` | Resetting `OrderProjection`'s `TrackingEventProcessor` token clears the in-memory read model and causes Axon Server to redeliver the entire event history, rebuilding it from scratch — demonstrates the read model is fully derived from the event log, not a separate source of truth |
| **Snapshotting** | `config/`, `command/` | After 5 events on a given `OrderAggregate` instance (`EventCountSnapshotTriggerDefinition`), Axon persists a snapshot; subsequent command handling loads the aggregate from the snapshot plus only the events since, instead of the full history — bounds replay cost as an aggregate's event count grows |

Business rule baked into `OrderAggregate`: cancelling an already-confirmed order is rejected (throws a domain exception), illustrating that invalid transitions leave no trace in the event store.

## REST API

All triggered via `DemoController` at `/demo/*`:

```
POST   /demo/orders                        create an order           — body: {customerId}
POST   /demo/orders/{orderId}/lines        add an order line          — body: {productId, quantity, price}
POST   /demo/orders/{orderId}/confirm      confirm an order           (FailureSimulator applies here, 5%)
POST   /demo/orders/{orderId}/cancel       cancel an order            (rejected if already confirmed)
GET    /demo/orders/{orderId}              query a single order summary
GET    /demo/orders                        query all order summaries
POST   /demo/orders/replay                 reset the projection and replay all events from Axon Server
```

Swagger UI: `http://localhost:8086/swagger-ui/index.html`.

## Error handling

Two distinct, separately demonstrated failure modes:

1. **Business rule violation** — e.g. `CancelOrderCommand` on an already-confirmed order. `OrderAggregate` throws a domain exception before applying any event; `DemoController` maps it to a 409. No event is ever recorded.
2. **Simulated infrastructure failure** — `FailureSimulator.maybeThrow("confirm-order")` inside the `ConfirmOrderCommand` handler (5% of calls), mapped to a 500. Same effect as above: the command is rejected and no event is applied, leaving the aggregate's event history (and therefore its state) untouched. This is the same `FailureSimulator` pattern mandated for `message-brokers/` modules (`FAILURE_RATE = 0.05`, `maybeThrow(String context)`, no boolean `shouldFail()`); reused here even though `cqrs-event-sourcing/` is a new category, because it's a genuinely good fit for showing that event sourcing never records partial/failed state changes.

## Spring Boot configuration

**Spring Boot version:** 3.4.x
**Java:** 21

**Dependencies:**
- `spring-boot-starter-web`
- `axon-spring-boot-starter` (Axon 4.13.x — compatible with Spring Boot 3.4)
- `springdoc-openapi-starter-webmvc-ui`
- `lombok`
- `spring-boot-starter-test` (test scope)
- `axon-test` (test scope — `AggregateTestFixture`)
- `gatling-charts-highcharts` (test scope, performance tests)

**`application.yml`** key settings:
- `axon.axonserver.servers: localhost:8124`
- `server.port: 8086`

**`AxonConfig`** (new — defines the snapshot trigger referenced by `OrderAggregate`'s `@Aggregate(snapshotTriggerDefinition = "orderSnapshotTriggerDefinition")`):
```java
@Bean
public SnapshotTriggerDefinition orderSnapshotTriggerDefinition(Snapshotter snapshotter) {
    return new EventCountSnapshotTriggerDefinition(snapshotter, 5);
}
```

**Query-side storage:** `OrderProjection` backs its read model with a `ConcurrentHashMap`, not a database — keeps the module self-contained like most `message-brokers/` demos. NoSQL/SQL persistence patterns are already covered by `noSQL/mongodb` and `backend/rest-api`; this demo's job is CQRS/event-sourcing mechanics, not storage technology.

**Command dispatch:** `DemoController` uses `CommandGateway.sendAndWait(...)` (synchronous) so REST semantics and tests stay simple, even though Axon's internal event distribution to the query side remains asynchronous.

## Testing

- **`OrderAggregateTest`** — Axon's `AggregateTestFixture` (given/when/then over commands and events), covering: order creation, line addition, confirmation, the cancel-after-confirm rejection, and snapshot triggering after 5 events.
- **`OrderProjectionTest`** — feeds events directly into the projection's `@EventHandler` methods and asserts the resulting read-model state.
- **`DemoControllerTest`** — `MockMvc`, with `CommandGateway`/`QueryGateway` mocked.
- **Gatling** — `src/test/java/.../performance/DemoSimulation.java`, exercising create → add-line → confirm → query, excluded from `mvn test` via the inherited surefire `**/performance/**` exclude in `cqrs-event-sourcing/pom.xml`, run explicitly via `mvn gatling:test`.

## README

`cqrs-event-sourcing/axon/README.md` follows the same format as `noSQL/mongodb/README.md`: prerequisites, start Axon Server, verify (dashboard UI), run app, curl examples per pattern, Swagger UI link, performance tests, architecture (command → aggregate → event store → tracking processor → projection diagram), patterns table, replay/snapshot explanation, stop.

`cqrs-event-sourcing/README.md` is a short index, analogous to `noSQL/README.md`, listing the `axon` module (ready to grow if more CQRS/ES frameworks are added later).

## Scope limits

- No sagas / process managers / distributed command bus across multiple aggregates — that's cross-aggregate orchestration, a meaningfully different (and more complex) topic from single-aggregate fundamentals; left for a possible future "advanced Axon patterns" addition.
- No subscription queries / reactive query streaming — fundamentals only, keeps the app non-reactive MVC like the rest of the repo.
- No JPA/database-backed read model — deliberate simplification; `noSQL/mongodb` already owns the persistence-technology story.
- `FailureSimulator` is wired only into `ConfirmOrderCommand`, not every command — keeps the demo focused, consistent with how other modules apply it selectively.
- Single aggregate type (`Order`) — no cross-aggregate consistency story, since that requires sagas (explicitly out of scope here).
- `message-brokers/README.md`'s broker comparison guide is not touched — Axon is not a message broker and doesn't belong in that table.
