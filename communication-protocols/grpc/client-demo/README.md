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
# {"productId":"p1","name":"Widget","priceCents":999}

curl -i http://localhost:8091/demo/grpc/unary/products/unknown
# HTTP/1.1 404 ...
```

**Server streaming — list the whole catalog:**

```bash
curl http://localhost:8091/demo/grpc/server-streaming/products
# [{"productId":"p1","name":"Widget","priceCents":999}, ...]
```

**Client streaming — upload a batch of orders, get one summary back:**

```bash
curl -X POST http://localhost:8091/demo/grpc/client-streaming/orders \
  -H 'Content-Type: application/json' \
  -d '[{"productId":"p1","quantity":2},{"productId":"p2","quantity":1}]'
# {"orderCount":2,"totalPriceCents":2997}
```

**Bidirectional streaming — send status updates, get each one echoed back acknowledged:**

```bash
curl -X POST http://localhost:8091/demo/grpc/bidi-streaming/order-status \
  -H 'Content-Type: application/json' \
  -d '[{"orderId":"o1","status":"PLACED"},{"orderId":"o1","status":"SHIPPED"}]'
# [{"orderId":"o1","status":"ACKNOWLEDGED:PLACED"},{"orderId":"o1","status":"ACKNOWLEDGED:SHIPPED"}]
```

**Simulated failure:** every RPC has a 5% chance of failing server-side (`FailureSimulator`). When it does, any of the above returns `502 Bad Gateway` with the gRPC status code and description in the body — repeat a call a few times to see it.

## Build & test

```bash
mvn clean package                    # build
mvn test                             # unit tests (Gatling excluded automatically)
mvn test -Dtest=ClassName            # single test class
mvn gatling:test                     # load test — requires both apps running first
```
