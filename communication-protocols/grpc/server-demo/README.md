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

## Build & test

```bash
mvn clean package                    # build (also regenerates gRPC stubs from src/main/proto/catalog.proto)
mvn test                             # unit tests
mvn test -Dtest=ClassName            # single test class
```
