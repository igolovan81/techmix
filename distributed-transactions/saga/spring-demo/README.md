# Saga Pattern Demo

A Spring Boot app demonstrating the Saga pattern for distributed transactions via **both** classic styles, over the same e-commerce checkout scenario (reserve inventory → charge payment → arrange shipping):

- **Choreography** (`com.testingai.saga.choreography`) — participants communicate only through Spring application events (`ApplicationEventPublisher`/`@EventListener`), no coordinator.
- **Orchestration** (`com.testingai.saga.orchestration`) — a central `SagaOrchestrator` calls each participant directly and drives compensation.

No external infrastructure required — everything is in-memory, in-process. Failures (and the resulting compensation cascade) are triggered deterministically via `failAt` on the checkout request, not randomly.

## Prerequisites

- Java 21
- Maven 3.9+

All commands below assume your working directory is `distributed-transactions/saga/spring-demo/`.

## Run the app

```bash
mvn spring-boot:run
```

## Architecture

### Choreography — happy path

```
POST /demo/saga/choreography/checkout
  → CheckoutRequested
  → OrderCreated            (OrderParticipant)
  → InventoryReserved       (InventoryParticipant)
  → PaymentProcessed        (PaymentParticipant)
  → ShipmentArranged        (ShippingParticipant)
  → OrderConfirmed          (OrderParticipant)
```

### Choreography — failure cascades

A failure at any step cascades **backward** one participant at a time — no participant needs to know the whole saga:

- `failAt=RESERVE_INVENTORY`: `OrderCreated → InventoryReservationFailed → OrderCancelled`
- `failAt=PROCESS_PAYMENT`: `InventoryReserved → PaymentFailed → InventoryReleased → OrderCancelled`
- `failAt=ARRANGE_SHIPPING`: `PaymentProcessed → ShipmentFailed → PaymentRefunded → InventoryReleased → OrderCancelled`

### Orchestration

`SagaOrchestrator` calls `InventoryParticipant.reserve` → `PaymentParticipant.charge` → `ShippingParticipant.arrange` directly. On a `StepOutcome.Failure`, it walks the completed steps **in reverse**, calling `compensate(orderId)` on each, and returns one `SagaResult` synchronously — no separate timeline to query.

## Patterns demonstrated

| Pattern | Where | What it shows |
|---|---|---|
| Choreography | `choreography/` package | Event-driven saga with no central coordinator; compensation propagates via events |
| Orchestration | `orchestration/` package | Central coordinator with explicit, synchronous compensation logic |
| Deterministic failure injection | `CheckoutRequest.failAt` | Reliable, repeatable demoing of the compensation path (vs. random `FailureSimulator`-style injection used elsewhere in this repo) |

## Try it — choreography

```bash
# Happy path
curl -s -X POST http://localhost:8089/demo/saga/choreography/checkout \
  -H "Content-Type: application/json" \
  -d '{"customerId":"customer-1","items":[{"productId":"p1","quantity":2,"unitPrice":9.99}],"failAt":null}'
# => {"orderId":"..."}

# Inspect the timeline (replace ORDER_ID)
curl -s http://localhost:8089/demo/saga/choreography/orders/ORDER_ID | jq

# Force a payment failure and watch the compensation cascade
curl -s -X POST http://localhost:8089/demo/saga/choreography/checkout \
  -H "Content-Type: application/json" \
  -d '{"customerId":"customer-1","items":[{"productId":"p1","quantity":2,"unitPrice":9.99}],"failAt":"PROCESS_PAYMENT"}'
# then GET the same order — timeline shows ORDER_CREATED, INVENTORY_RESERVED, PAYMENT_FAILED, INVENTORY_RELEASED, ORDER_CANCELLED
```

## Try it — orchestration

```bash
# Happy path — one synchronous result
curl -s -X POST http://localhost:8089/demo/saga/orchestration/checkout \
  -H "Content-Type: application/json" \
  -d '{"customerId":"customer-1","items":[{"productId":"p1","quantity":2,"unitPrice":9.99}],"failAt":null}' | jq

# Force a shipping failure — result shows compensatedSteps: [PROCESS_PAYMENT, RESERVE_INVENTORY]
curl -s -X POST http://localhost:8089/demo/saga/orchestration/checkout \
  -H "Content-Type: application/json" \
  -d '{"customerId":"customer-1","items":[{"productId":"p1","quantity":2,"unitPrice":9.99}],"failAt":"ARRANGE_SHIPPING"}' | jq
```

## Swagger UI

http://localhost:8089/swagger-ui/index.html

## Run performance tests

```bash
mvn gatling:test
```

Requires the app to already be running in a separate terminal.
