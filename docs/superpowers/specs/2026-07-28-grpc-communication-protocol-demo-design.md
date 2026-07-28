# gRPC Communication Protocol Demo Design

**Date:** 2026-07-28
**Status:** Approved

## Overview

A new top-level `communication-protocols/` category (sibling to `message-brokers/`, `noSQL/`, `cqrs-event-sourcing/`, `template-engines/`, `distributed-transactions/`), containing a `grpc/` module with **two independent Spring Boot apps** — `server-demo` and `client-demo` — that communicate only over gRPC. Together they demonstrate all four gRPC RPC patterns (unary, server streaming, client streaming, bidirectional streaming) against a small product-catalog/order domain, consistent with the domain used elsewhere in this repo (`template-engines`).

`server-demo` owns the `.proto` contract and implements the service using `net.devh:grpc-server-spring-boot-starter`. `client-demo` uses `net.devh:grpc-client-spring-boot-starter` to obtain injected stubs and exposes a REST `DemoController` — one endpoint per RPC pattern — so the demo can be driven with `curl`/Swagger like every other module in this repo, while the actual client↔server traffic is genuine gRPC.

No external infrastructure is required (no Docker) — both apps run locally via `mvn spring-boot:run`, like `template-engines/` and `distributed-transactions/saga`.

## Repository structure

```
communication-protocols/
├── pom.xml                                          (new parent POM, mirrors template-engines/pom.xml)
├── eclipse-formatter.xml                            (copy — same style repo-wide)
├── README.md                                        (category overview: the 4 RPC patterns, client/server relationship)
└── grpc/
    ├── README.md                                    (protocol overview, run-both-apps instructions, patterns table)
    ├── server-demo/
    │   ├── pom.xml                                  (artifactId: grpc-server-demo)
    │   ├── README.md
    │   └── src/
    │       ├── main/
    │       │   ├── proto/
    │       │   │   └── catalog.proto                (ProductCatalogService — see Proto contract below)
    │       │   ├── java/com/testingai/grpc/server/
    │       │   │   ├── GrpcServerDemoApplication.java
    │       │   │   ├── domain/
    │       │   │   │   ├── Product.java              (record: id, name, priceCents)
    │       │   │   │   ├── Order.java                 (record: id, productId, quantity)
    │       │   │   │   └── SampleDataService.java     (in-memory product catalog + order store, same shape as
    │       │   │   │                                    template-engines' SampleDataService)
    │       │   │   ├── service/
    │       │   │   │   └── ProductCatalogServiceImpl.java  (extends generated ProductCatalogServiceGrpc.ProductCatalogServiceImplBase;
    │       │   │   │                                          implements all 4 RPCs)
    │       │   │   └── util/
    │       │   │       └── FailureSimulator.java      (FAILURE_RATE = 0.05, maybeThrow(String context) — same
    │       │   │                                        shape as message-brokers/kafka's FailureSimulator)
    │       │   └── resources/
    │       │       └── application.yml                (grpc.server.port: 9090, server.port: 9091 — actuator only)
    │       └── test/
    │           └── java/com/testingai/grpc/server/
    │               ├── GrpcServerDemoApplicationTest.java
    │               ├── service/
    │               │   └── ProductCatalogServiceImplTest.java   (StreamObserver mocks; asserts FailureSimulator
    │               │                                               path maps to Status.INTERNAL)
    │               └── util/
    │                   └── FailureSimulatorTest.java
    └── client-demo/
        ├── pom.xml                                  (artifactId: grpc-client-demo)
        ├── README.md
        └── src/
            ├── main/
            │   ├── java/com/testingai/grpc/client/
            │   │   ├── GrpcClientDemoApplication.java
            │   │   └── controller/
            │   │       ├── DemoController.java        (all endpoints below)
            │   │       └── DemoExceptionHandler.java   (maps StatusRuntimeException → HTTP error body with
            │   │                                         gRPC status code + description)
            │   └── resources/
            │       └── application.yml                 (server.port: 8091; grpc.client.catalog-service.address:
            │                                              static://localhost:9090)
            └── test/
                └── java/com/testingai/grpc/client/
                    ├── GrpcClientDemoApplicationTest.java
                    ├── controller/
                    │   ├── DemoControllerTest.java       (MockMvc; mocked blocking/async stub beans)
                    │   └── DemoIntegrationTest.java       (boots server-demo's service impl on an in-process
                    │                                        grpc transport + client-demo's Spring context
                    │                                        together; exercises all 4 RPCs end-to-end)
                    └── performance/
                        └── DemoSimulation.java            (Gatling; hits client-demo's REST endpoints)
```

### Cross-cutting fixes needed in existing files

- **`.githooks/pre-commit`** — extend the staged-file grep from `^(message-brokers|noSQL|cqrs-event-sourcing|template-engines|distributed-transactions|spring-boot-starters)/.*\.java$` to also match `^communication-protocols/.*\.java$`, and add a matching `mvn spotless:apply` block run from `communication-protocols/`.
- **`CLAUDE.md`** — add a "gRPC communication protocol demo" command section (mirroring the Saga pattern demo section, since it's also a two-command reactor build with no infra), a `communication-protocols/` row in the repository layout table, and a note that `server-demo` must be started before `client-demo` for the demo to work end-to-end.

## Proto contract (`server-demo/src/main/proto/catalog.proto`)

```protobuf
syntax = "proto3";

package catalog;

option java_package = "com.testingai.grpc.server.proto";
option java_multiple_files = true;

service ProductCatalogService {
  rpc GetProduct(ProductRequest) returns (ProductResponse);
  rpc ListProducts(ListProductsRequest) returns (stream ProductResponse);
  rpc UploadOrders(stream OrderRequest) returns (OrderSummary);
  rpc StreamOrderStatus(stream OrderStatusUpdate) returns (stream OrderStatusUpdate);
}

message ProductRequest {
  string product_id = 1;
}

message ProductResponse {
  string product_id = 1;
  string name = 2;
  int64 price_cents = 3;
}

message ListProductsRequest {}

message OrderRequest {
  string product_id = 1;
  int32 quantity = 2;
}

message OrderSummary {
  int32 order_count = 1;
  int64 total_price_cents = 2;
}

message OrderStatusUpdate {
  string order_id = 1;
  string status = 2;
}
```

`client-demo` depends on `server-demo`'s generated stub classes via the standard `protobuf-maven-plugin` + `protoc-gen-grpc-java` codegen running in both modules against the same `.proto` file (copied via `build-helper-maven-plugin` source-attach from `server-demo`'s `src/main/proto`, the common pattern for sharing a `.proto` across two Maven modules without a third "contracts" module — kept to two modules since a dedicated contracts module would be overkill for one small `.proto` file).

## Server (`server-demo`)

`ProductCatalogServiceImpl`:

- **`getProduct`** — unary. Looks up the product in `SampleDataService`; calls `FailureSimulator.maybeThrow("getProduct")` first. `NOT_FOUND` if the id doesn't exist, `INTERNAL` (via caught `RuntimeException` from the simulator) on simulated failure.
- **`listProducts`** — server streaming. Iterates the in-memory catalog, calling `responseObserver.onNext(...)` per product with `FailureSimulator.maybeThrow("listProducts")` checked once per item, then `onCompleted()`.
- **`uploadOrders`** — client streaming. Returns a `StreamObserver<OrderRequest>` that accumulates count/total as each order arrives (`onNext`), and on `onCompleted()` calls `responseObserver.onNext(OrderSummary)` + `onCompleted()`. `FailureSimulator.maybeThrow("uploadOrders")` checked per incoming order.
- **`streamOrderStatus`** — bidi streaming. Returns a `StreamObserver<OrderStatusUpdate>` that, for each incoming update, immediately echoes back an acknowledgement update (`status = "ACKNOWLEDGED:" + incoming.status`) via the response observer — a live back-and-forth, not a batch collect-then-reply.

All four map a caught `RuntimeException` from `FailureSimulator` to `Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException()` passed to `responseObserver.onError(...)`, which is the standard gRPC error-propagation path this demo exists to show.

## Client (`client-demo`)

`DemoController`:

| Endpoint | RPC pattern | Behavior |
|---|---|---|
| `GET /api/demo/unary/products/{id}` | Unary | Calls `getProduct` via the blocking stub; `200` with `ProductResponse` body, `404` if `NOT_FOUND` status, `502` with gRPC status code+description on `INTERNAL`. |
| `GET /api/demo/server-streaming/products` | Server streaming | Calls `listProducts` via the blocking stub's iterator; collects all items into a `List<ProductResponse>` and returns `200`. |
| `POST /api/demo/client-streaming/orders` | Client streaming | Body: `List<OrderRequest>`. Opens the async stub's `uploadOrders` request observer, pushes each order, `onCompleted()`; a `StreamObserver` + `CountDownLatch` adapter blocks the HTTP thread until the server's single `OrderSummary` (or error) arrives, then returns it as `200`. |
| `POST /api/demo/bidi-streaming/order-status` | Bidi streaming | Body: `List<OrderStatusUpdate>`. Opens the async stub's `streamOrderStatus` duplex call, pushes each update, collects each echoed acknowledgement via the same latch-adapter pattern, `onCompleted()` once all acks are in, returns the full list as `200`. |

`DemoExceptionHandler` is a `@RestControllerAdvice` catching `StatusRuntimeException` specifically (mapping `Status.Code` → HTTP status: `NOT_FOUND`→404, `INTERNAL`→502, default→500) plus a fallback handler for anything else, matching the `saga` module's `DemoExceptionHandler` convention.

Swagger UI at `/swagger-ui/index.html` on `client-demo` (the app with the REST surface); `server-demo` has no HTTP API to document, so no springdoc dependency there.

## Testing

- **`server-demo`**: `ProductCatalogServiceImplTest` — direct calls to the service impl with mocked `StreamObserver<T>` (Mockito), verifying `onNext`/`onCompleted`/`onError` calls for each RPC's happy path and its `FailureSimulator`-triggered error path (asserting the captured `StatusRuntimeException`'s code is `INTERNAL`). `FailureSimulatorTest` — statistical assertion over many invocations, same style as the Kafka module's test.
- **`client-demo`**: `DemoControllerTest` — `MockMvc` (`@WebMvcTest` is fine here; there's no Spock in this module, see [[spock-spring-webmvctest-incompatibility]] for why that matters only for Spock-based modules) with the gRPC stub beans mocked, covering each endpoint's happy path and its `StatusRuntimeException` → HTTP mapping. `DemoIntegrationTest` — `@SpringBootTest` on `client-demo` wired to an **in-process** gRPC server (`InProcessServerBuilder`/`InProcessChannelBuilder`, grpc-java's standard test transport) hosting a real `ProductCatalogServiceImpl` instance, exercising all four endpoints end-to-end through real (in-process) gRPC calls — this is the module's equivalent of `RequestLoggingIntegrationTest` in `spring-boot-starters`.
- **`client-demo/src/test/.../performance/DemoSimulation.java`** — Gatling load test hitting all four REST endpoints on a running `client-demo` (which in turn must have a running `server-demo` behind it); excluded from `mvn test` via the inherited surefire `**/performance/**` exclude, run explicitly via `mvn gatling:test`.
- No Gatling/perf test in `server-demo` — Gatling drives HTTP, and `server-demo` has no HTTP surface; this matches the repo-wide convention that `DemoSimulation` lives with the REST-facing app.

## Ports

Every HTTP `server.port` currently in use across the repo's demo modules: `8081`–`8090` (SQS, ASB, Pulsar, task-automation-agent/mongodb, code-review-agent, axon, handlebars, freemarker, saga/sdlc-agent, request-logging). `9090` (grpc-spring-boot-starter's default gRPC port) is unused.

- `server-demo` → gRPC `9090`; HTTP `9091` (actuator health only — kept clearly outside the `808x` HTTP block since this app's real interface is gRPC, not REST).
- `client-demo` → HTTP `8091` (next free slot in the `808x` block).

## Spring Boot configuration

**Spring Boot version:** 3.4.x
**Java:** 21

**`server-demo` dependencies:** `net.devh:grpc-server-spring-boot-starter`, `spring-boot-starter-actuator` (health only), `protobuf-java`, `grpc-stub`/`grpc-protobuf`, `lombok`, `spring-boot-starter-test` (test), `grpc-testing` (test, for `InProcessServerBuilder` reuse if needed on the server side too).

**`client-demo` dependencies:** `net.devh:grpc-client-spring-boot-starter`, `spring-boot-starter-web`, `springdoc-openapi-starter-webmvc-ui`, `lombok`, `spring-boot-starter-test` (test), `grpc-testing` (test), `gatling-charts-highcharts` (test).

`protobuf-maven-plugin` (with `protoc` + `grpc-java` codegen plugin, via `os-maven-plugin` for platform detection) generates stub/message classes into `target/generated-sources` in `server-demo`; `client-demo` consumes the same generated classes by depending on `server-demo`'s jar (a client only needs the generated stub, not the actual service implementation, so this is the standard "client depends on server's generated code" pattern for a two-module gRPC demo without a separate contracts module).

## README

- `communication-protocols/README.md` — short category index (analogous to `distributed-transactions/README.md`), room to grow if more protocol demos are added later (e.g. GraphQL, WebSocket).
- `communication-protocols/grpc/README.md` — protocol-level overview: what gRPC is, HTTP/2 + Protobuf in one paragraph, the 4-pattern table, and the client/server relationship (must run `server-demo` first).
- `communication-protocols/grpc/server-demo/README.md` and `.../client-demo/README.md` — each follows the `saga`/`request-logging` module README format: prerequisites (Java 21, Maven, no Docker), run instructions, and (for `client-demo`) full `curl` walkthroughs for all four endpoints including a forced-failure example (repeated calls until the 5% `FailureSimulator` trips, to show the `502` error body).

## Scope limits

- No TLS — both apps use plaintext gRPC (`usePlaintext()` on the client channel), matching the "local demo, not production hardening" scope of every other module in this repo. Called out explicitly in the module README as a deliberate simplification.
- No retries/deadlines/interceptors beyond what's needed to demo the 4 RPC patterns and basic error mapping — a full resilience story (client-side retry policies, deadlines, interceptor-based auth) is out of scope, same spirit as `saga`'s "no retry logic on individual steps" scope limit.
- No persistence — `SampleDataService` is in-memory only, matching `template-engines`.
- Only one `.proto` service — no attempt to demo multiple services, reflection, or health-checking protocol extensions built into gRPC, to keep the demo focused on the 4 RPC-pattern story.
