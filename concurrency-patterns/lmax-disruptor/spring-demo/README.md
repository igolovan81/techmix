# LMAX Disruptor Demo

Learning and demonstration project for the [LMAX Disruptor](https://lmax-exchange.github.io/disruptor/) — a lock-free, single-writer ring buffer for high-throughput, low-latency inter-thread messaging — applied to a small trading order-matching domain.

## Why no locks?

The ring buffer is a pre-allocated, fixed-size array of reusable `OrderEvent` objects. A single writer claims the next slot with an atomic increment (no locking); each consumer tracks its own cursor and only reads slots the producer has already published, coordinated through `Sequence` objects and a `SequenceBarrier` rather than a mutex. Because events are mutable and reused (not allocated per message), there is no garbage-collection pressure from the hot path either.

## Prerequisites

- Java 21
- Maven

No Docker/external infrastructure required — everything runs in-process.

## Running

```bash
mvn spring-boot:run
```

The app starts on `http://localhost:8100`. Swagger UI: `http://localhost:8100/swagger-ui/index.html`.

## Patterns demonstrated

| Pattern | Endpoint | What it shows |
|---|---|---|
| Single handler | `POST /demo/disruptor/single` | The minimal Disruptor setup: one `EventHandler` consuming the ring buffer sequentially. |
| Parallel handlers | `POST /demo/disruptor/parallel` | Two independent handlers (journal, risk-check) processing every event concurrently — `handleEventsWith(a, b)`. |
| Diamond dependency graph | `POST /demo/disruptor/diamond` | The classic LMAX pattern: journal + replication run in parallel, then a matching-engine handler runs only after both finish — `handleEventsWith(a, b).then(c)`. |
| Producer comparison | `POST /demo/disruptor/producer` | `ProducerType.SINGLE` vs. `ProducerType.MULTI`, compared under concurrent publishers. |
| Wait-strategy comparison | `POST /demo/disruptor/waitstrategy` | `BlockingWaitStrategy` vs. `YieldingWaitStrategy` vs. `BusySpinWaitStrategy`, compared for throughput/latency. |
| Exception handling | `POST /demo/disruptor/errors` | A custom `ExceptionHandler` + a `FailureSimulator` (5% rate) show the ring buffer surviving a handler exception without stopping. |

### Diamond dependency graph

```
                 ┌──────────────┐
       ┌────────▶│   Journal    │──────┐
       │         └──────────────┘      │
Order ─┤                                ├──▶ MatchingHandler ──▶ Fill(s)
       │         ┌──────────────┐      │
       └────────▶│ Replication  │──────┘
                 └──────────────┘
```

`MatchingHandler` only processes an event once both `Journal` and `Replication` have processed it — enforced by the Disruptor's sequence barrier, not application code.

## Walkthrough

All endpoints accept an optional `eventCount` query parameter (default `1000`, capped at `100000`).

```bash
# Single handler
curl -X POST "http://localhost:8100/demo/disruptor/single?eventCount=2000"

# Parallel handlers
curl -X POST "http://localhost:8100/demo/disruptor/parallel?eventCount=2000"

# Diamond dependency graph — returns fills produced by the matching engine
curl -X POST "http://localhost:8100/demo/disruptor/diamond?eventCount=2000"

# SINGLE vs MULTI producer comparison
curl -X POST "http://localhost:8100/demo/disruptor/producer?eventCount=2000&threads=4"

# Wait-strategy comparison — expect BUSY_SPIN lowest latency/highest CPU,
# BLOCKING highest latency/lowest CPU; results are hardware-dependent
curl -X POST "http://localhost:8100/demo/disruptor/waitstrategy?eventCount=5000"

# Exception handling — ~5% of events simulate a handler failure;
# succeeded + failed always equals eventCount
curl -X POST "http://localhost:8100/demo/disruptor/errors?eventCount=2000"
```

## Testing

```bash
mvn test                # unit tests (Gatling excluded automatically)
mvn test -Dtest=ClassName
mvn gatling:test         # load test — requires the app running first (mvn spring-boot:run)
```

## Scope limits

- No persistence — the order book and all counters are in-memory only, reset on restart.
- `OrderMatchingEngine` is intentionally minimal: no order cancellation, no partial-fill edge cases beyond basic quantity decrementing.
- `errors/` demonstrates survival and observability of a handler failure, not recovery/replay of the failed event.
- `producer/`'s `SINGLE` run always publishes from exactly one thread, even if a higher `threads` value is requested — concurrent publishing to a `SINGLE`-type ring buffer is undefined behavior.
