# gRPC Client Demo

REST facade over `ProductCatalogService`. Each endpoint below makes one genuine gRPC call to [server-demo](../server-demo/) (which must already be running on `localhost:9090`) and translates the result to/from JSON.

## Prerequisites

Java 21, Maven. No Docker.

## Run

```bash
cd communication-protocols
mvn -pl grpc/server-demo spring-boot:run   # terminal 1 — must be running first
mvn -pl grpc/client-demo spring-boot:run   # terminal 2
```

Swagger UI: http://localhost:8091/swagger-ui/index.html

## Walkthrough

**Unary — get one product:**

```bash
curl http://localhost:8091/demo/grpc/unary/products/p1
# {"productId":"p1","name":"Mini Widget","priceCents":636}

curl -i http://localhost:8091/demo/grpc/unary/products/unknown
# HTTP/1.1 404 ...
```

**Server streaming — list the whole catalog (40 products):**

```bash
curl http://localhost:8091/demo/grpc/server-streaming/products
# [{"productId":"p1","name":"Mini Widget","priceCents":636}, ...]
```

Watch the `server-demo` console while this runs — with the default `demo.stream-delay-millis: 300` pacing, this call takes ~12 seconds and logs each of the 40 products as it's sent, one line at a time.

**Client streaming — upload a batch of orders, get one summary back:**

```bash
curl -X POST http://localhost:8091/demo/grpc/client-streaming/orders \
  -H 'Content-Type: application/json' \
  -d '[{"productId":"p1","quantity":2},{"productId":"p2","quantity":1}]'
# {"orderCount":2,"totalPriceCents":2045}
```

**Bidirectional streaming — send status updates, get each one echoed back acknowledged:**

```bash
curl -X POST http://localhost:8091/demo/grpc/bidi-streaming/order-status \
  -H 'Content-Type: application/json' \
  -d '[{"orderId":"o1","status":"PLACED"},{"orderId":"o1","status":"SHIPPED"}]'
# [{"orderId":"o1","status":"ACKNOWLEDGED:PLACED"},{"orderId":"o1","status":"ACKNOWLEDGED:SHIPPED"}]
```

**Simulated failure:** every RPC has a 5% chance of failing server-side (`FailureSimulator`). When it does, any of the above returns `502 Bad Gateway` with the gRPC status code and description in the body — repeat a call a few times to see it.

## Watching the streaming patterns

Both apps log at `INFO` with a `[RpcName]` tag on every request, per-item send/receive, and completion, so running `server-demo` and `client-demo` in two terminals and watching the console gives a clear play-by-play of each pattern:

- `ListProducts` (server streaming) — `client-demo` logs `[ListProducts] received product #N: ...` as each item arrives; `server-demo` logs the matching `[ListProducts] sending product N/40: ...` on the other side.
- `UploadOrders` (client streaming) — `client-demo` logs `[UploadOrders] sending order: ...` per item pushed, then one `[UploadOrders] received summary: ...` at the end; `server-demo` logs `[UploadOrders] received order N: ...` per item.
- `StreamOrderStatus` (bidirectional streaming) — both sides log every message independently and in real time: `[StreamOrderStatus] sending update: ...` / `received update: ...` and `[StreamOrderStatus] received ack: ...` / `acknowledging: ...`.

## Request correlation

Every REST call gets a short id (e.g. `a1b2c3d4`) that `DemoController` generates and logs, and `RequestIdClientInterceptor` (a `@GrpcGlobalClientInterceptor`, applied to every outgoing call automatically) attaches as an `x-request-id` gRPC metadata header. `server-demo` reads that same header and logs it too — see [server-demo/README.md](../server-demo/README.md#request-correlation) — so a single call's log lines look like this across both consoles:

```
# client-demo
[ListProducts][a1b2c3d4] requesting product catalog
[ListProducts][a1b2c3d4] received product #1: Mini Widget (p1)
...

# server-demo
[ListProducts][a1b2c3d4] streaming 40 products
[ListProducts][a1b2c3d4] sending product 1/40: Mini Widget (p1)
...
```

`grep a1b2c3d4` in either terminal picks out just that request's lines.

## Build & test

```bash
mvn clean package                    # build
mvn test                             # unit tests (Gatling excluded automatically)
mvn test -Dtest=ClassName            # single test class
mvn gatling:test                     # Gatling load test — requires both apps running first (see below)
mvn verify -Pjmeter-load-test        # JMeter load test — requires both apps running first (see below)
```

Both load tests drive the same four endpoints with the same shape of traffic — 2 users ramped a few seconds apart, each pausing 500ms between its unary/server-streaming/client-streaming/bidi-streaming calls, with bigger batches (12 orders, 8 status updates) than the walkthrough above — designed to be watched in both apps' logs rather than to measure throughput. Pick whichever tool you're more comfortable with; they're interchangeable for this demo.

- **Gatling**: `com.testingai.grpc.client.performance.DemoSimulation` (`src/test/java/.../performance/`). Excluded from `mvn test` automatically; run with `mvn gatling:test`. HTML report under `target/gatling/`.
- **JMeter**: `src/test/jmeter/DemoSimulation.jmx` — open it in the JMeter GUI to inspect or edit it visually, either with a local JMeter install (`jmeter -t src/test/jmeter/DemoSimulation.jmx`) or via the plugin (`mvn jmeter:configure jmeter:gui`), no separate install needed. `jmeter-maven-plugin` is only wired up behind the `jmeter-load-test` Maven profile (its `configure`/`jmeter` goals aren't ad-hoc invokable like Gatling's, and binding them to the default build would mean any `mvn verify` tries to hit a live HTTP server) — so `mvn clean package`/`mvn verify` without `-Pjmeter-load-test` never touches JMeter. Raw per-sample results (CSV) land in `target/jmeter/results/`; a summary is also printed to the console as the run progresses.
