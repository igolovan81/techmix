# Webhooks Communication Protocol Demo Design

**Date:** 2026-07-30
**Status:** Approved

## Overview

A new `webhooks/` module under `communication-protocols/` (sibling to `grpc/` and `graphql/`), containing **two** independent Spring Boot apps — `producer-demo` and `consumer-demo` — mirroring the gRPC demo's client/server split. Webhooks are inherently two-sided: one party fires HTTP callbacks on events, the other registers a URL and receives them. Splitting into two apps lets each side's concerns live in its own codebase: delivery/retry/signing on the producer, verification/dedup on the consumer.

The domain is order lifecycle events (`order.created`, `order.paid`, `order.shipped`, `order.cancelled`) — its own independent model, not shared code with `distributed-transactions/saga` or `workflow-engines/camunda`, chosen only because the name is already familiar across this repo.

No external infrastructure is required (no Docker) — both apps run locally via `mvn spring-boot:run`, like `grpc/` and `graphql/`.

## Patterns implemented (parallels gRPC's four-row / GraphQL's five-row README table)

| Pattern | Where | What it demonstrates |
|---|---|---|
| Subscription registration | producer-demo `POST /subscriptions` | A consumer registers a callback URL, shared secret, and event types before any delivery starts |
| HMAC signature verification | producer signs, consumer verifies | Standard webhook security pattern: the receiver proves the payload actually came from the sender and wasn't tampered with in transit |
| Retry with exponential backoff | producer-demo `WebhookDispatcher` | A failed delivery (consumer down/erroring) is retried with increasing delay instead of being dropped or hammering the receiver |
| Dead-lettering | producer-demo `WebhookDispatcher` | After exhausting retries, the delivery is parked for inspection instead of retried forever |
| Idempotency / dedup | consumer-demo `WebhookReceiverController` | A retried delivery (same `X-Webhook-Id`) is recognized and not reprocessed — the flip side of at-least-once delivery |

## Repository structure

```
communication-protocols/
├── README.md                                          (add Webhooks row to the protocol table)
└── webhooks/
    ├── README.md                                      (protocol overview: what webhooks are, pattern table, running instructions)
    ├── producer-demo/
    │   ├── pom.xml                                    (artifactId: webhooks-producer-demo; no separate README — walkthrough lives in webhooks/README.md covering both apps)
    │   └── src/
    │       ├── main/
    │       │   ├── java/com/testingai/webhooks/producer/
    │       │   │   ├── WebhooksProducerDemoApplication.java
    │       │   │   ├── subscription/
    │       │   │   │   ├── Subscription.java              (record: id, callbackUrl, secret, eventTypes)
    │       │   │   │   ├── SubscriptionService.java        (in-memory ConcurrentHashMap store)
    │       │   │   │   └── SubscriptionController.java     (POST/GET /subscriptions, DELETE /subscriptions/{id})
    │       │   │   ├── event/
    │       │   │   │   ├── OrderEvent.java                 (record: eventType, orderId, occurredAt, data)
    │       │   │   │   └── OrderEventController.java       (POST /orders/{orderId}/events/{eventType})
    │       │   │   ├── delivery/
    │       │   │   │   ├── DeliveryStatus.java              (enum: PENDING, SUCCEEDED, RETRYING, DEAD_LETTERED)
    │       │   │   │   ├── DeliveryAttempt.java             (mutable record-ish state: id, subscriptionId, eventType, attemptCount, status, nextRetryAt)
    │       │   │   │   ├── WebhookDispatcher.java           (signs, sends via RestClient, schedules retries via TaskScheduler)
    │       │   │   │   └── DeliveryController.java          (GET /deliveries, GET /deliveries/dead-letter)
    │       │   │   ├── security/
    │       │   │   │   └── HmacSigner.java                  (HMAC-SHA256, hex-encoded)
    │       │   │   ├── config/
    │       │   │   │   └── DispatchConfig.java              (RestClient bean, TaskScheduler bean)
    │       │   │   └── exception/
    │       │   │       └── DemoExceptionHandler.java        (same shape as grpc/client-demo's)
    │       │   └── resources/
    │       │       └── application.yml                      (server.port: 8096)
    │       └── test/
    │           ├── java/com/testingai/webhooks/producer/
    │           │   ├── WebhooksProducerDemoApplicationTest.java
    │           │   ├── subscription/SubscriptionServiceTest.java
    │           │   ├── security/HmacSignerTest.java
    │           │   ├── delivery/WebhookDispatcherTest.java   (uses OkHttp MockWebServer to stub the callback URL)
    │           │   └── event/OrderEventControllerTest.java
    │           ├── performance/DemoSimulation.java            (Gatling — POST /orders/{id}/events/{type} against a healthy consumer-demo instance)
    │           └── jmeter/DemoSimulation.jmx                   (same requests, jmeter-load-test profile)
    └── consumer-demo/
        ├── pom.xml                                    (artifactId: webhooks-consumer-demo)
        └── src/
            ├── main/
            │   ├── java/com/testingai/webhooks/consumer/
            │   │   ├── WebhooksConsumerDemoApplication.java
            │   │   ├── receiver/
            │   │   │   ├── WebhookReceiverController.java   (POST /webhooks/orders — verify, dedup, record)
            │   │   │   ├── ReceivedEvent.java                (record: deliveryId, eventType, orderId, receivedAt, duplicate)
            │   │   │   └── ReceivedEventStore.java           (in-memory list + seen-id ConcurrentHashMap.newKeySet())
            │   │   ├── security/
            │   │   │   └── HmacVerifier.java                 (recompute + constant-time compare against configured secret)
            │   │   ├── failure/
            │   │   │   ├── FailureSimulationController.java  (POST /admin/simulate-failures?count=N)
            │   │   │   └── FailureSimulationState.java        (AtomicInteger countdown)
            │   │   └── admin/
            │   │       └── AdminController.java               (GET /admin/received)
            │   └── resources/
            │       └── application.yml                        (server.port: 8097; webhook.secret: consumer-demo-secret-change-me)
            └── test/java/com/testingai/webhooks/consumer/
                ├── WebhooksConsumerDemoApplicationTest.java
                ├── security/HmacVerifierTest.java
                ├── receiver/WebhookReceiverControllerTest.java  (MockMvc: valid signature, invalid signature -> 401, replay -> dedup)
                └── failure/FailureSimulationControllerTest.java
```

### Cross-cutting fixes needed in existing files

- **`CLAUDE.md`** — add a "Webhooks communication protocol demo" command section (two apps, producer must be started before consumer registers against it — mirrors the gRPC section's shape) and rows for both new modules in the repository layout table.
- **`communication-protocols/pom.xml`** — add `webhooks/producer-demo` and `webhooks/consumer-demo` to `<modules>`.
- **`.githooks/pre-commit`** — already greps `^communication-protocols/.*\.java$` and runs `mvn spotless:apply` there; no change needed, new modules are already covered by the path pattern.

## Subscription & dispatch flow

1. User starts `consumer-demo` (port 8097), then `producer-demo` (port 8096).
2. User registers a subscription: `POST http://localhost:8096/subscriptions` with `{"callbackUrl": "http://localhost:8097/webhooks/orders", "secret": "consumer-demo-secret-change-me", "eventTypes": ["order.created", "order.paid"]}`. `secret` must match consumer-demo's configured `webhook.secret` — in a real system the subscriber would generate this and communicate it out of band; here it's just plain config on both sides for demo simplicity.
3. User triggers an event: `POST http://localhost:8096/orders/order-123/events/created`.
4. `OrderEventController` builds the `OrderEvent`, asks `SubscriptionService` for subscriptions matching `order.created`, and calls `WebhookDispatcher.dispatch(subscription, event)` for each — each dispatch gets its own UUID delivery id and its own `DeliveryAttempt` record.
5. `WebhookDispatcher` serializes the event to JSON, computes `HmacSigner.sign(secret, rawJson)`, and POSTs via `RestClient` to `callbackUrl` with headers `X-Webhook-Id`, `X-Webhook-Event`, `X-Webhook-Signature: sha256=<hex>`.
6. On 2xx response: `DeliveryAttempt.status = SUCCEEDED`.
7. On any other outcome (non-2xx, timeout, connection refused): increment `attemptCount`, set `status = RETRYING`, and schedule the next attempt via `TaskScheduler.schedule(...)` at `now + backoff(attemptCount)` where `backoff` is `1s, 2s, 4s, 8s, 16s` for attempts 1–5.
8. After the 5th failed attempt: `status = DEAD_LETTERED`, no further retries scheduled, logged at `WARN`.

On the consumer side, `WebhookReceiverController`:
1. Reads `X-Webhook-Id`, `X-Webhook-Event`, `X-Webhook-Signature`, and the raw body.
2. If `FailureSimulationState`'s counter is `> 0`: decrement it and return `500` immediately (simulating an outage/bug), without touching signature/dedup logic — this is what makes producer-side retry/backoff/dead-letter observable on demand.
3. Verifies the signature via `HmacVerifier`; mismatch → `401`, request discarded.
4. If `X-Webhook-Id` is already in the seen-set → record it in `ReceivedEventStore` as `duplicate: true`, return `200` without reprocessing.
5. Otherwise: add to seen-set, record as `duplicate: false`, log the order event, return `200`.

## Ports

`8096` (producer-demo), `8097` (consumer-demo) — next free slots after `8095` (`reactive-programming/project-reactor/upstream-demo`).

## Spring Boot configuration

**Spring Boot version:** 3.4.4 (inherited from the parent POM)
**Java:** 21

**producer-demo dependencies:** `spring-boot-starter-web`, `lombok`, `spring-boot-starter-test` (test), `okhttp mockwebserver` (test, same as `reactive-programming/project-reactor/spring-demo`'s pattern), `gatling-charts-highcharts` (test).

**consumer-demo dependencies:** `spring-boot-starter-web`, `lombok`, `spring-boot-starter-test` (test).

Neither app needs Spring Security — HMAC verification is the module's security mechanism and is implemented directly in the controller, not via a filter chain.

## Testing

- **`SubscriptionServiceTest`** — add/list/delete, filtering by event type.
- **`HmacSignerTest`** / **`HmacVerifierTest`** — known input/secret produces a stable, expected hex digest; verifier accepts a matching signature and rejects a tampered body or wrong secret.
- **`WebhookDispatcherTest`** — spins up an OkHttp `MockWebServer` as the fake callback target:
  - first response 200 → `SUCCEEDED`, no retry scheduled
  - first N responses 500, then 200 → `SUCCEEDED` after N+1 attempts, asserts backoff delays via the server's request timestamps
  - all 5 responses 500 → `DEAD_LETTERED`, no 6th request sent
  - asserts `X-Webhook-Signature` on every request matches `HmacSigner.sign(secret, body)` for that exact body
- **`OrderEventControllerTest`** — triggering an event only dispatches to subscriptions whose `eventTypes` include it.
- **`WebhookReceiverControllerTest`** (MockMvc) — valid signature → 200 + recorded; invalid signature → 401; replayed `X-Webhook-Id` → 200 + `duplicate: true` + not re-logged as new; failure-simulation armed → 500 and counter decremented.
- **`FailureSimulationControllerTest`** — arming with `count=N` causes exactly N subsequent failures then reverts to normal.
- **`WebhooksProducerDemoApplicationTest`** / **`WebhooksConsumerDemoApplicationTest`** — context loads, same as other modules' `*ApplicationTest`.
- **`performance/DemoSimulation.java`** (producer-demo only, Gatling) — POSTs `/orders/{id}/events/{type}` repeatedly against a consumer-demo instance with no failures armed, so throughput reflects the happy path; excluded from `mvn test` via the inherited surefire `**/performance/**` exclude.
- **`src/test/jmeter/DemoSimulation.jmx`** — same requests, wired behind the `jmeter-load-test` Maven profile.

## README

- `communication-protocols/README.md` — add a Webhooks row ("Two independent Spring Boot apps — producer (dispatch/retry/dead-letter) + consumer (verify/dedup)"). The closing "More protocol demos may be added here over time (e.g. WebSocket)" sentence is left as-is — WebSocket is still a future addition, not this one.
- `communication-protocols/webhooks/README.md` — protocol-level overview (what webhooks are: server-to-server push over plain HTTP, contrasted with polling), the pattern table above, prerequisites, run order (consumer-demo first, then producer-demo, since the subscription callback URL must exist before it's registered — though registration itself is just data, so the ordering is about being ready to *receive*, not a hard requirement), and a full curl walkthrough:
  1. register a subscription
  2. trigger `order.created`, observe `GET /deliveries` show `SUCCEEDED`
  3. arm `POST /admin/simulate-failures?count=3` on consumer-demo
  4. trigger another event, watch console logs show retries at 1s/2s/4s, then success on the 4th attempt via `GET /deliveries`
  5. arm `count=10` (exceeds the 5-attempt budget), trigger an event, observe `GET /deliveries/dead-letter` after all 5 attempts are exhausted
  6. re-POST the same delivery manually (copy the `X-Webhook-Id`/body from a producer log line) directly to the consumer to demonstrate dedup via `GET /admin/received` showing `duplicate: true`

## Scope limits

- No persistence — everything is in-memory `ConcurrentHashMap`/lists, matching every other module in this repo. A restart loses all subscriptions and delivery history.
- No signature algorithm negotiation, no secret rotation, no per-event-type sub-secrets — one secret per subscription, HMAC-SHA256 only, matching GitHub/Stripe's baseline pattern without their full feature set.
- No persistent dead-letter replay endpoint (e.g. "redeliver this dead-lettered event") — dead-letters are inspectable via `GET /deliveries/dead-letter` but the demo doesn't wire up a manual-replay action, since that would just re-exercise the same dispatch path already covered by the retry test.
- Retry backoff is fixed (`1s, 2s, 4s, 8s, 16s`, 5 attempts) and not configurable via `application.yml` — kept as a constant for simplicity, called out explicitly in the module README as a deliberate simplification (same spirit as GraphQL's "no query complexity limiting" scope note).
- Single-instance only — no distributed delivery-attempt store, no leader election, no clustering considerations.
