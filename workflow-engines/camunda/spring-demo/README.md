# Camunda Demo (Spring Boot)

Spring Boot app driving the `order-fulfillment` BPMN process on Camunda 8, covering service tasks, an exclusive gateway, a user task, and error-boundary failure routing.

## Prerequisites

Java 21, Maven, Docker (for both the Camunda 8 compose stack below and, separately, for `mvn test`'s Testcontainers-backed integration tests).

## Run

```bash
cd workflow-engines/camunda
docker compose -f docker/docker-compose.yml up -d
cd spring-demo
mvn spring-boot:run
```

Operate: http://localhost:8080/operate — watch process instances move through the diagram live as you run the examples below.

## Walkthrough

**Low-value order — completes without approval:**

```bash
curl -s http://localhost:8093/demo/camunda/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"cust-1","items":[{"productId":"p1","quantity":1,"unitPrice":10.00}]}'
# {"orderId":"...","processInstanceKey":...}

curl -s http://localhost:8093/demo/camunda/orders/<orderId>
# {"orderId":"...","processInstanceKey":...,"status":"FULFILLED","completedSteps":["RESERVE_INVENTORY","PROCESS_PAYMENT","ARRANGE_SHIPPING"]}
```

**High-value order — requires approval:**

```bash
curl -s http://localhost:8093/demo/camunda/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"cust-1","items":[{"productId":"p1","quantity":1,"unitPrice":999.00}]}'
# {"orderId":"<orderId>",...} — status is PENDING_APPROVAL; check Tasklist (http://localhost:8080/tasklist) to see the pending "Approve Order" task, or:

curl -s http://localhost:8093/demo/camunda/orders/<orderId>
# {"status":"PENDING_APPROVAL","completedSteps":["RESERVE_INVENTORY"], ...}

curl -s -X POST http://localhost:8093/demo/camunda/orders/<orderId>/approval \
  -H 'Content-Type: application/json' \
  -d '{"approved":true}'

curl -s http://localhost:8093/demo/camunda/orders/<orderId>
# {"status":"FULFILLED", ...}
```

**Rejecting a high-value order:** same as above, but `{"approved":false}` — the order ends up `CANCELLED` after the `Release Inventory` cleanup step.

**Simulated failures (deterministic, via `failAt` — not random):**

```bash
curl -s http://localhost:8093/demo/camunda/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"cust-1","items":[{"productId":"p1","quantity":1,"unitPrice":10.00}],"failAt":"RESERVE_INVENTORY"}'
# order ends up CANCELLED with an empty completedSteps list — the "Inventory Unavailable" error boundary event fires
# straight after the failed reservation attempt, routing directly to Order Cancelled (nothing to release, since
# reservation never succeeded)

curl -s http://localhost:8093/demo/camunda/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"cust-1","items":[{"productId":"p1","quantity":1,"unitPrice":10.00}],"failAt":"PROCESS_PAYMENT"}'
# order ends up CANCELLED with completedSteps ["RESERVE_INVENTORY"] — the "Payment Declined" error boundary event
# fires after a successful reservation, routing through Release Inventory (this time inventory *was* reserved) to
# Order Cancelled
```

Job workers process asynchronously, so the order's status may briefly read `IN_PROGRESS`/`PENDING_APPROVAL` for a moment right after starting/advancing it — re-`GET` a second later if you see that.

## Build & test

```bash
mvn clean package                    # build
mvn test                             # unit + integration tests (Gatling excluded automatically) — requires a working Docker daemon
mvn test -Dtest=ClassName            # single test class
mvn gatling:test                     # Gatling load test — requires the app AND the docker-compose stack running first
```

Unlike every other module in this repo, `mvn test` here needs Docker: Camunda's official `camunda-process-test-spring` library (`@CamundaSpringProcessTest`) spins up a real, ephemeral Camunda runtime per test class via Testcontainers. This is separate from (and doesn't need) the `docker compose` stack used to actually run the app — the compose stack is only for `mvn spring-boot:run` and the Gatling load test.

- **Gatling**: `com.testingai.camunda.performance.DemoSimulation` (`src/test/java/.../performance/`). Excluded from `mvn test` automatically; run with `mvn gatling:test` against a running app. HTML report under `target/gatling/`. Covers only the low-value happy-path flow — no JMeter counterpart for this module (a second tool duplicating one scenario isn't worth the maintenance here, unlike `communication-protocols`' dual-tool convention).
