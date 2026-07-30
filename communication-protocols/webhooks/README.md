# Webhooks Demo

Demonstrates webhooks — asynchronous server-to-server push over plain HTTP, where the receiver registers a callback URL once and the sender calls it whenever an event happens, instead of the receiver polling for changes — via two independent Spring Boot apps:

- **[producer-demo](producer-demo/)** — owns the subscription registry and the `WebhookDispatcher`: signs every delivery with HMAC-SHA256, retries failures with exponential backoff, and dead-letters deliveries that exhaust their retry budget.
- **[consumer-demo](consumer-demo/)** — receives deliveries, verifies the HMAC signature, deduplicates by delivery id, and exposes an admin endpoint to arm deterministic failures so retry/backoff/dead-letter behavior is observable on demand.

Unlike gRPC's client/server split (both apps speak the same generated stub), producer and consumer here only ever talk plain signed JSON over HTTP — either side could be replaced with a service in any language without touching the other.

## The five patterns

| Pattern | Where | What it demonstrates |
|---|---|---|
| Subscription registration | producer-demo `POST /subscriptions` | A consumer registers a callback URL, shared secret, and event types before any delivery starts |
| HMAC signature verification | producer signs, consumer verifies | The receiver proves the payload actually came from the sender and wasn't tampered with in transit |
| Retry with exponential backoff | producer-demo `WebhookDispatcher` | A failed delivery is retried with increasing delay instead of dropped or hammering the receiver |
| Dead-lettering | producer-demo `WebhookDispatcher` | After exhausting retries, the delivery is parked for inspection instead of retried forever |
| Idempotency / dedup | consumer-demo `WebhookReceiverController` | A retried delivery (same `X-Webhook-Id`) is recognized and not reprocessed — the flip side of at-least-once delivery |

### Subscription registration

**Pros**
- Receivers opt in only to the event types they care about
- No polling — the producer decides when to push
- Multiple independent subscribers can register against the same event stream

**Cons**
- The producer must track subscriber state (persisted in real systems; in-memory here)
- A subscriber must expose a publicly reachable HTTP endpoint
- No discovery mechanism — the subscriber must already know the producer's registration endpoint

**Typical use cases**
- Payment gateways notifying merchants of charge/refund events
- CI providers notifying external tools of build status
- SaaS platforms notifying integrations of resource changes

### HMAC signature verification

**Pros**
- Receiver can cryptographically prove the payload came from the real producer and wasn't altered in transit
- No shared session/token needed per request — just a static secret
- Cheap to compute compared to asymmetric signing

**Cons**
- Secret must be provisioned out-of-band and kept in sync on both sides
- No secret rotation story here — a deliberate demo simplification
- Doesn't protect against replay by itself — only tampering/spoofing (see idempotency below)

**Typical use cases**
- Every major webhook provider (GitHub, Stripe, Slack) signs payloads this way
- Any server-to-server callback over the public internet where the receiver can't otherwise trust the caller

### Retry with exponential backoff

**Pros**
- Transient receiver outages (deploys, brief overload) don't lose events
- Exponential spacing avoids hammering a struggling receiver
- Bounded attempt count keeps a single failing delivery from retrying forever

**Cons**
- Delivery is delayed, sometimes significantly, when the receiver is down
- The producer must hold delivery state until it succeeds or exhausts retries
- Retried deliveries can arrive out of order relative to newer events

**Typical use cases**
- Any at-least-once webhook delivery guarantee
- Recovering from a receiver's short maintenance window without manual intervention

### Dead-lettering

**Pros**
- A permanently failing delivery is parked for inspection instead of retried forever or silently dropped
- Operators can see exactly what failed and why
- Keeps the retry scheduler from accumulating unbounded pending work

**Cons**
- Dead-lettered events need a manual (or separately built) replay path to actually recover — not built here
- Still requires an operator to notice and act — this demo has no alerting
- Represents a genuine loss of real-time delivery for that event

**Typical use cases**
- The DLQ pattern from message brokers, applied to webhook delivery instead of message consumption
- Surfacing integration failures (e.g. a customer's misconfigured endpoint) for support follow-up

### Idempotency / dedup

**Pros**
- Safe to retry aggressively on the producer side without the receiver double-processing
- Protects against duplicate delivery from any source, not just this producer's own retries
- Simple to implement — one id per delivery, one seen-set on the receiver

**Cons**
- Receiver must remember every delivery id it's seen (unbounded here — a demo simplification; production systems expire old ids)
- Only works if the producer's delivery id stays stable across retries of the *same* logical delivery
- Doesn't address ordering — a duplicate is recognized, but out-of-order delivery is a separate concern

**Typical use cases**
- Any at-least-once delivery system paired with retries (which is: almost all webhook providers)
- Payment/order webhooks specifically, where double-processing has real consequences

## Prerequisites

Java 21, Maven. No Docker.

## Run

Consumer must be up first so it's ready to receive (registration itself is just data, so this ordering is about being ready to receive, not a hard requirement):

```bash
cd communication-protocols
mvn -pl webhooks/consumer-demo spring-boot:run
```

In a second terminal:

```bash
cd communication-protocols
mvn -pl webhooks/producer-demo spring-boot:run
```

## Walkthrough

**1. Register a subscription** (consumer-demo's callback URL, with the secret matching its `webhook.secret` in `application.yml`):

```bash
curl -s -X POST http://localhost:8096/subscriptions \
  -H 'Content-Type: application/json' \
  -d '{"callbackUrl":"http://localhost:8097/webhooks/orders","secret":"consumer-demo-secret-change-me","eventTypes":["order.created","order.paid"]}'
```

**2. Trigger an event and watch it succeed:**

```bash
curl -s -X POST http://localhost:8096/orders/order-123/events/created
curl -s http://localhost:8096/deliveries
```

The delivery shows `"status":"SUCCEEDED"` after a moment; `curl -s http://localhost:8097/admin/received` shows it recorded with `"duplicate":false`.

**3. Arm 3 deterministic failures on the consumer, then trigger another event:**

```bash
curl -s -X POST "http://localhost:8097/admin/simulate-failures?count=3"
curl -s -X POST http://localhost:8096/orders/order-124/events/created
```

Watch producer-demo's console: attempts 1–3 fail, retrying after 1s/2s/4s, then attempt 4 succeeds. `curl -s http://localhost:8096/deliveries` shows `"attemptCount":4,"status":"SUCCEEDED"`.

**4. Arm more failures than the retry budget allows, to see dead-lettering:**

```bash
curl -s -X POST "http://localhost:8097/admin/simulate-failures?count=10"
curl -s -X POST http://localhost:8096/orders/order-125/events/created
```

After 5 failed attempts (backoff of 1s/2s/4s/8s/16s — this step takes about 31 seconds to fully resolve), `curl -s http://localhost:8096/deliveries/dead-letter` shows the delivery parked with `"status":"DEAD_LETTERED"`.

**5. Demonstrate dedup by replaying a delivery directly at the consumer.** Copy the exact `X-Webhook-Id`, `X-Webhook-Signature`, and body from step 2's successful delivery out of producer-demo's console log, then:

```bash
curl -s -i -X POST http://localhost:8097/webhooks/orders \
  -H 'Content-Type: application/json' \
  -H 'X-Webhook-Id: <same-delivery-id-as-step-2>' \
  -H 'X-Webhook-Event: order.created' \
  -H 'X-Webhook-Signature: <same-signature-as-step-2>' \
  -d '<same-body-as-step-2>'
```

`curl -s http://localhost:8097/admin/received` now shows two entries for that delivery id — the first with `"duplicate":false`, the second `"duplicate":true`.

## Scope limits

- No persistence — everything is in-memory, single-instance only.
- One secret per subscription, HMAC-SHA256 only — no algorithm negotiation, no secret rotation.
- Retry backoff (1s/2s/4s/8s/16s, 5 attempts) is fixed, not configurable via `application.yml`.
- No manual dead-letter replay endpoint.

## Build & test

```bash
cd communication-protocols
mvn test -pl webhooks/producer-demo,webhooks/consumer-demo
mvn gatling:test -pl webhooks/producer-demo               # Gatling load test — requires both apps running first
mvn verify -Pjmeter-load-test -pl webhooks/producer-demo   # JMeter load test — requires both apps running first
```
