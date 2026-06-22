# Axon Framework Demo

A Spring Boot app demonstrating CQRS/event-sourcing fundamentals with [Axon Framework](https://www.axoniq.io/products/axon-framework) and [Axon Server](https://www.axoniq.io/products/axon-server) as the event store and command/query router.

## Prerequisites

- Java 21
- Maven
- Docker (for Axon Server)

## Start Axon Server

```bash
docker compose -f docker/docker-compose.yml up -d
```

Wait ~15 seconds, then verify it's healthy:

```bash
curl -f http://localhost:8024/actuator/health
```

Open the dashboard at [http://localhost:8024](http://localhost:8024) to browse aggregates, events, and snapshots as you exercise the API below.

## Run the app

```bash
cd spring-demo
mvn spring-boot:run
```

The app starts on port `8086` and connects to Axon Server at `localhost:8124`.

Swagger UI: [http://localhost:8086/swagger-ui/index.html](http://localhost:8086/swagger-ui/index.html)

## Architecture

```
REST request
    │
    ▼
DemoController ──CommandGateway──▶ OrderAggregate (event-sourced)
                                        │ apply(event)
                                        ▼
                                  Axon Server (event store)
                                        │ TrackingEventProcessor
                                        ▼
                                  OrderProjection (read model)
    ▲
    │ QueryGateway
DemoController
```

## Patterns demonstrated

| Pattern | Where | What it shows |
|---|---|---|
| Command handling + event sourcing | `command/OrderAggregate.java` | Commands are validated and turned into events; aggregate state is rebuilt purely by replaying those events |
| CQRS query model | `query/OrderProjection.java` | A separate, denormalized read model updated asynchronously from the same events |
| Replay | `replay/ReplayService.java` | Resetting the tracking processor clears and rebuilds the read model from Axon Server's full event history |
| Snapshotting | `config/AxonConfig.java` | After 5 events on one `OrderAggregate`, Axon persists a snapshot, bounding future replay cost |

## Try it

```bash
# Create an order
ORDER_ID=$(curl -s -X POST http://localhost:8086/demo/orders \
  -H "Content-Type: application/json" -d '{"customerId":"customer-1"}')

# Add a few order lines (5+ triggers a snapshot — watch the Axon Server dashboard)
for i in 1 2 3 4 5 6; do
  curl -X POST "http://localhost:8086/demo/orders/$ORDER_ID/lines" \
    -H "Content-Type: application/json" \
    -d "{\"productId\":\"product-$i\",\"quantity\":1,\"price\":9.99}"
done

# Confirm it
curl -X POST "http://localhost:8086/demo/orders/$ORDER_ID/confirm"

# Query the read model
curl "http://localhost:8086/demo/orders/$ORDER_ID"
curl "http://localhost:8086/demo/orders"

# Rebuild the read model from the event store
curl -X POST "http://localhost:8086/demo/orders/replay"
```

Cancelling a confirmed order is rejected:

```bash
curl -i -X POST "http://localhost:8086/demo/orders/$ORDER_ID/cancel"
# HTTP/1.1 409 — "Cannot cancel order ... after it is confirmed"
```

## Performance tests

```bash
cd spring-demo
mvn gatling:test
```

Requires the app and Axon Server running first.

## Stop Axon Server

```bash
docker compose -f docker/docker-compose.yml down
```

Add `-v` to also remove the event store volumes.
