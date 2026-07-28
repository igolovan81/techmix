# gRPC Server Demo

Implements `ProductCatalogService` — the gRPC service used by [client-demo](../client-demo/) to demonstrate all four RPC patterns.

## Prerequisites

Java 21, Maven. No Docker — the product catalog is in-memory.

## Run

```bash
cd communication-protocols
mvn -pl grpc/server-demo spring-boot:run
```

The gRPC server listens on `localhost:9090` (plaintext). An HTTP port (`9091`) is exposed only for `/actuator/health`.

## Patterns implemented

| RPC | Pattern |
|---|---|
| `GetProduct` | Unary |
| `ListProducts` | Server streaming |
| `UploadOrders` | Client streaming |
| `StreamOrderStatus` | Bidirectional streaming |

Every RPC calls `FailureSimulator.maybeThrow(...)` first (5% chance), which is caught and turned into a `Status.INTERNAL` gRPC error — see [client-demo/README.md](../client-demo/README.md) for how that surfaces over REST.

## Demo data and observability

`SampleDataService` generates 40 products (10 themed names × 4 variants, e.g. "Mini Widget", "Pro Gizmo") rather than a handful of hardcoded items, so the streaming patterns have enough items to be visibly gradual instead of finishing instantly.

Every RPC logs at `INFO` with a `[RpcName]` tag — request received, each item sent/received, and a completion summary — so you can watch `ListProducts`, `UploadOrders`, and `StreamOrderStatus` progress live in the console. Those three streaming RPCs also pause `demo.stream-delay-millis` (default `300`ms, configurable in `application.yml`) between items, so a full `ListProducts` call over the 40-product catalog takes around 12 seconds — deliberately slow enough to watch. Set `demo.stream-delay-millis=0` to disable the pacing (e.g. for scripted/CI runs).

## Request correlation

Every log line is also tagged with a short request id, e.g. `[ListProducts][a1b2c3d4] sending product 3/40: ...`. `RequestIdServerInterceptor` (a `@GrpcGlobalServerInterceptor`, so it applies to every RPC automatically) reads the id from the `x-request-id` gRPC metadata header that `client-demo` attaches to each call — see [client-demo/README.md](../client-demo/README.md#request-correlation) — and generates a fallback id if a caller doesn't send one. The id is stored in a `Context.Key`, which gRPC propagates automatically to the streaming callbacks (`onNext`/`onCompleted`) even though those run on a different thread than the initial call.

Because both apps log the same id, you can grep one request's full journey across both consoles, e.g. `grep a1b2c3d4` in each terminal.

## Build & test

```bash
mvn clean package                    # build (also regenerates gRPC stubs from src/main/proto/catalog.proto)
mvn test                             # unit tests
mvn test -Dtest=ClassName            # single test class
```
