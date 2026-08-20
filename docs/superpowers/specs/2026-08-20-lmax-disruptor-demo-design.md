# LMAX Disruptor Demo Design

**Date:** 2026-08-20
**Status:** Draft

## Overview

A new top-level `concurrency-patterns/` category (sibling to `distributed-transactions/`, `reactive-programming/`, etc.), containing a single Spring Boot demo app — `lmax-disruptor/spring-demo` — that demonstrates the [LMAX Disruptor](https://lmax-exchange.github.io/disruptor/) ring-buffer concurrency library against the domain it was invented for: a trading order-matching engine. No external infrastructure required — everything runs in-process, like `distributed-transactions/` and `template-engines/`.

Six patterns are covered, each its own package with its own REST endpoint:

- **single** — one `EventHandler` consuming the ring buffer (the minimal Disruptor setup)
- **parallel** — two independent handlers (journal, risk-check) processing the same event concurrently
- **diamond** — the signature LMAX dependency graph: journal + replication run in parallel, then a matching-engine handler runs only after both finish (`.then(...)`)
- **producer** — `ProducerType.SINGLE` vs `ProducerType.MULTI` compared under concurrent publishers
- **waitstrategy** — `BlockingWaitStrategy` vs `YieldingWaitStrategy` vs `BusySpinWaitStrategy` compared for throughput/latency
- **errors** — a custom `ExceptionHandler` + `FailureSimulator` (5% rate) showing the ring buffer surviving a handler exception

## Repository structure

```
concurrency-patterns/
├── pom.xml                                          (new parent POM, mirrors distributed-transactions/pom.xml)
├── eclipse-formatter.xml                            (copy — same style repo-wide)
├── README.md                                        (category overview: pattern table)
└── lmax-disruptor/
    └── spring-demo/
        ├── pom.xml                                  (artifactId: lmax-disruptor-demo; adds com.lmax:disruptor)
        ├── README.md
        └── src/
            ├── main/
            │   ├── java/com/testingai/disruptor/
            │   │   ├── DisruptorDemoApplication.java
            │   │   ├── domain/
            │   │   │   ├── Side.java                    (enum: BUY, SELL)
            │   │   │   ├── Order.java                    (record: orderId, symbol, Side side, int quantity, BigDecimal price — REST request body)
            │   │   │   ├── OrderEvent.java                (mutable ring-buffer event: same fields as Order, plus a clear()/set(...) method)
            │   │   │   ├── OrderEventFactory.java          (implements EventFactory<OrderEvent>)
            │   │   │   └── Fill.java                      (record: symbol, buyOrderId, sellOrderId, quantity, price)
            │   │   ├── matching/
            │   │   │   └── OrderMatchingEngine.java        (per-symbol TreeMap price-time-priority book; match(OrderEvent) -> List<Fill> or resting)
            │   │   ├── single/
            │   │   │   ├── SingleHandlerService.java       (persistent Disruptor; @PostConstruct start / @PreDestroy shutdown; one EventHandler)
            │   │   │   └── SingleHandlerResult.java        (record: eventsProcessed, elapsedMillis, throughputPerSecond)
            │   │   ├── parallel/
            │   │   │   ├── ParallelHandlersService.java    (persistent Disruptor; handleEventsWith(journal, riskCheck))
            │   │   │   ├── JournalHandler.java
            │   │   │   ├── RiskCheckHandler.java
            │   │   │   └── ParallelResult.java             (record: journalCount, riskCheckCount, elapsedMillis)
            │   │   ├── diamond/
            │   │   │   ├── DiamondService.java             (persistent Disruptor; handleEventsWith(journal, replication).then(matching))
            │   │   │   ├── ReplicationHandler.java
            │   │   │   ├── MatchingHandler.java            (delegates to OrderMatchingEngine, accumulates Fills)
            │   │   │   └── DiamondResult.java              (record: fills List<Fill>, restingOrders, elapsedMillis)
            │   │   ├── producer/
            │   │   │   ├── ProducerComparisonService.java  (builds + tears down one ephemeral Disruptor per ProducerType per request)
            │   │   │   └── ProducerStat.java                (record: producerType, threadCount, elapsedMillis, throughputPerSecond)
            │   │   ├── waitstrategy/
            │   │   │   ├── WaitStrategyComparisonService.java (builds + tears down one ephemeral Disruptor per WaitStrategy per request)
            │   │   │   └── WaitStrategyStat.java             (record: strategyName, elapsedMillis, throughputPerSecond, avgLatencyMicros)
            │   │   ├── errors/
            │   │   │   ├── ErrorsService.java               (persistent Disruptor; custom ExceptionHandler<OrderEvent>; @PostConstruct/@PreDestroy)
            │   │   │   ├── ErrorsResult.java                 (record: succeeded, failed, elapsedMillis)
            │   │   │   └── util/FailureSimulator.java        (FAILURE_RATE = 0.05; maybeThrow(String context) — Kafka-module convention)
            │   │   └── controller/
            │   │       ├── DemoController.java              (all endpoints below)
            │   │       └── DemoExceptionHandler.java
            │   └── resources/
            │       └── application.yml                     (server.port: 8100)
            └── test/
                ├── java/com/testingai/disruptor/
                │   ├── DisruptorDemoApplicationTest.java
                │   ├── matching/OrderMatchingEngineTest.java
                │   ├── single/SingleHandlerServiceTest.java
                │   ├── parallel/ParallelHandlersServiceTest.java
                │   ├── diamond/DiamondServiceTest.java
                │   ├── producer/ProducerComparisonServiceTest.java
                │   ├── waitstrategy/WaitStrategyComparisonServiceTest.java
                │   ├── errors/
                │   │   ├── ErrorsServiceTest.java
                │   │   └── util/FailureSimulatorTest.java
                │   ├── controller/DemoControllerTest.java    (MockMvc, one happy-path case per endpoint)
                │   └── performance/DemoSimulation.java
                └── resources/application.yml
```

### Cross-cutting fixes needed in existing files

- **`.githooks/pre-commit`** — extend the staged-file grep to also match `^concurrency-patterns/.*\.java$`, and add a matching `mvn spotless:apply` block run from `concurrency-patterns/`.
- **`CLAUDE.md`** — add an "LMAX Disruptor demo" command section (mirroring the saga/DDD sections), a `concurrency-patterns/` row in the repository layout table.
- **`README.md`** (repo root) — add a `concurrency-patterns/` row to the layout table.

## Domain model

```java
public enum Side { BUY, SELL }
public record Order(String orderId, String symbol, Side side, int quantity, BigDecimal price) {}
```

`OrderEvent` is the mutable, pre-allocated ring-buffer payload (the standard Disruptor idiom — objects are reused, not allocated per publish): the same fields as `Order`, plus a `set(...)` method the publishing translator calls, and a no-arg constructor for `OrderEventFactory`.

`OrderMatchingEngine` keeps one `TreeMap<BigDecimal, Deque<OrderEvent>>` book per side per symbol (price-time priority). `match(OrderEvent incoming)` crosses the incoming order against the opposite book while prices cross, producing zero or more `Fill`s; any unmatched remainder rests in the book. This is intentionally minimal — no partial-fill edge cases beyond basic quantity decrementing, no order cancellation — enough to make `diamond/`'s matching handler a recognizable matching engine rather than a black box.

## Pattern implementations

### `single/` — SingleHandlerService

One persistent `Disruptor<OrderEvent>` (ring buffer size 2048, `ProducerType.SINGLE`, `BlockingWaitStrategy`), started in `@PostConstruct` and shut down in `@PreDestroy` (the `AutoCloseable`-resource pattern already mandated in `.claude/rules/code-review.md` for long-running Spring components, e.g. `ServiceBusProcessorClient`). One `EventHandler<OrderEvent>` logs/counts each event and counts down a per-request `CountDownLatch`. The controller publishes `eventCount` orders via `ringBuffer.publishEvent(translator, order)`, then awaits the latch before returning `SingleHandlerResult`.

### `parallel/` — ParallelHandlersService

Same persistent-bean shape, but `disruptor.handleEventsWith(journalHandler, riskCheckHandler)` — both handlers receive every event independently and concurrently (each on its own `BatchEventProcessor` thread). Each handler counts its own events into an `AtomicLong` and counts down its own latch; the controller awaits both before returning `ParallelResult`.

### `diamond/` — DiamondService

`disruptor.handleEventsWith(journalHandler, replicationHandler).then(matchingHandler)` — the classic LMAX pattern: journal and replication both run in parallel against every event, and `matchingHandler` only processes an event once *both* upstream handlers have processed it (enforced by the Disruptor's sequence barrier, not by application code). `matchingHandler` delegates to `OrderMatchingEngine` and accumulates `Fill`s + resting-order count for the response.

### `producer/` — ProducerComparisonService

Cannot be a single persistent bean: `ProducerType` is fixed at `Disruptor` construction. For each request, builds one `Disruptor<OrderEvent>` with `ProducerType.SINGLE` and one with `ProducerType.MULTI`, each with a single counting handler, then publishes `eventCount` orders via `RingBuffer.publishEvent`. Concurrent publishing to a `SINGLE`-type ring buffer is undefined behavior, so the `SINGLE` run always publishes from exactly 1 thread regardless of the requested `threadCount`; the `MULTI` run splits `eventCount` across `threadCount` publisher threads. Both instances are shut down and the endpoint returns a `List<ProducerStat>`. This is a deliberate exception to the persistent-bean pattern used elsewhere in this module, called out in the module README: the whole point of this endpoint is comparing different Disruptor *configurations*, which is incompatible with one long-lived instance.

### `waitstrategy/` — WaitStrategyComparisonService

Same ephemeral-instance reasoning as `producer/`: for each of `BlockingWaitStrategy`, `YieldingWaitStrategy`, `BusySpinWaitStrategy`, builds a fresh single-producer `Disruptor<OrderEvent>`, publishes `eventCount` orders from one thread, measures elapsed time and per-event latency (timestamp captured at publish, compared to timestamp captured when the handler processes it), tears the instance down, and returns a `List<WaitStrategyStat>` ordered fastest-to-slowest. Module README notes the expected ordering (busy-spin lowest latency/highest CPU, blocking highest latency/lowest CPU) as context for interpreting the numbers, while making clear results are container/hardware-dependent.

### `errors/` — ErrorsService

Persistent bean, same lifecycle pattern as `single/`. The one `EventHandler` calls `FailureSimulator.maybeThrow("order-processing")` before processing; a custom `ExceptionHandler<OrderEvent>` registered via `disruptor.setDefaultExceptionHandler(...)` catches the `RuntimeException`, logs it, and increments a failure counter instead of letting it propagate — demonstrating that one handler's exception does not stop the ring buffer or block later events. Counts succeeded/failed into `ErrorsResult`.

## API surface

Single `DemoController`, consistent with every other module in the repo:

| Endpoint | Behavior |
|---|---|
| `POST /demo/disruptor/single?eventCount=1000` | Publishes N orders through the single-handler ring buffer; returns `SingleHandlerResult`. |
| `POST /demo/disruptor/parallel?eventCount=1000` | Publishes N orders through the two parallel handlers; returns `ParallelResult`. |
| `POST /demo/disruptor/diamond?eventCount=1000` | Publishes N orders through the diamond graph; returns `DiamondResult`. |
| `POST /demo/disruptor/producer?eventCount=1000&threads=4` | Runs the SINGLE- and MULTI-producer comparison; returns `List<ProducerStat>`. |
| `POST /demo/disruptor/waitstrategy?eventCount=10000` | Runs the three wait-strategy comparisons; returns `List<WaitStrategyStat>`. |
| `POST /demo/disruptor/errors?eventCount=1000` | Publishes N orders with simulated 5% handler failures; returns `ErrorsResult`. |

`eventCount` defaults to 1000, capped at 100000 (enforced in the controller, `400` via `DemoExceptionHandler` if exceeded). Swagger UI at `/swagger-ui/index.html`.

## Testing

- Unit tests per service (`single/`, `parallel/`, `diamond/`, `errors/`): publish a small known batch, assert counts/results, assert the bean's Disruptor actually shuts down cleanly (no thread leak) via `@PreDestroy` invocation in the test.
- `OrderMatchingEngineTest` — crossing orders produce expected `Fill`s; non-crossing orders rest in the book.
- `ProducerComparisonServiceTest` / `WaitStrategyComparisonServiceTest` — small `eventCount`, assert all configurations reach the expected total count (correctness), not specific timing numbers (timing is inherently non-deterministic and out of scope for assertions).
- `FailureSimulatorTest` — matches the Kafka-module convention test shape (statistical check over many iterations that failures occur within an expected rate band).
- `DemoControllerTest` — `@WebMvcTest` + `MockMvc` (plain JUnit 5, matching `distributed-transactions/saga`'s convention — no Spock, per [[spock-spring-webmvctest-incompatibility]]), one happy-path case per endpoint plus the `eventCount` cap validation.
- `src/test/.../performance/DemoSimulation.java` — Gatling load test hitting all six endpoints with modest event counts; excluded from `mvn test` via the inherited surefire `**/performance/**` exclude, run explicitly via `mvn gatling:test`.

## Ports

- `lmax-disruptor/spring-demo` → `8100` (next free slot after `domain-driven-design/banking/spring-demo`'s `8099`).

## Spring Boot configuration

**Spring Boot version:** 3.4.x
**Java:** 21

**`lmax-disruptor-demo` dependencies:** `spring-boot-starter-web`, `springdoc-openapi-starter-webmvc-ui`, `lombok`, `com.lmax:disruptor` (latest 4.x — requires Java 21, matching this repo's baseline), `spring-boot-starter-test` (test), `gatling-charts-highcharts` (test). No persistence, no database driver.

## README

`concurrency-patterns/lmax-disruptor/spring-demo/README.md` follows the saga module's format: prerequisites (Java 21, Maven — no Docker needed), run instructions (`mvn spring-boot:run`), a diagram per pattern (especially the diamond dependency graph), a patterns-demonstrated table, and full `curl` walkthroughs for all six endpoints. Includes a short primer on what the ring buffer is and why it avoids locks (single-writer principle, pre-allocated events, sequence barriers) since this is the first Disruptor demo in the repo. Swagger UI link and Gatling instructions included.

`concurrency-patterns/README.md` is a short category index (analogous to `distributed-transactions/README.md`), listing the six patterns and their "best fit" use case, ready to grow if more concurrency primitives are added later (e.g. Java's own `java.util.concurrent` structured-concurrency demos).

## Scope limits

- No persistence layer — the order book and all counters are in-memory only, reset on restart; this is a concurrency-pattern demo, not a durable matching engine.
- No order cancellation or partial-fill edge cases beyond basic quantity decrementing in `OrderMatchingEngine` — enough to make `diamond/` concrete without building a production matching engine.
- No `ExceptionHandler`-triggered retry — `errors/` demonstrates survival and observability of a handler failure, not recovery/replay of the failed event.
- No multi-JVM / cross-process Disruptor usage — the Disruptor is an in-process library by design; this demo does not attempt to make it look like a distributed system.
- `producer/`'s `SINGLE` producer type is deliberately exercised with a single publishing thread even when `threads>1` is requested, because concurrent publishing to a `SINGLE`-type ring buffer is undefined behavior — this constraint is surfaced in the response/README, not silently worked around.
