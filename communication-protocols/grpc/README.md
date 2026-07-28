# gRPC Demo

Demonstrates gRPC — a high-performance RPC framework built on HTTP/2 and Protocol Buffers — via two independent Spring Boot apps that talk to each other over real gRPC:

- **[server-demo](server-demo/)** — owns the `.proto` contract (`ProductCatalogService`) and implements all four RPCs against an in-memory product catalog.
- **[client-demo](client-demo/)** — exposes a REST facade (`DemoController`) that drives each RPC pattern via a genuine gRPC call, so the whole demo can be exercised with `curl`.

## The four RPC patterns

| Pattern | RPC | What it demonstrates |
|---|---|---|
| Unary | `GetProduct` | Simple request/response — the gRPC equivalent of a single REST call |
| Server streaming | `ListProducts` | Server pushes a sequence of responses over one call |
| Client streaming | `UploadOrders` | Client pushes a sequence of requests, server replies once at the end |
| Bidirectional streaming | `StreamOrderStatus` | Client and server exchange messages independently over the same call |

## Running the demo

`server-demo` must be started first — `client-demo` connects to it at `localhost:9090` on startup.

```bash
cd communication-protocols
mvn -pl grpc/server-demo spring-boot:run   # terminal 1
mvn -pl grpc/client-demo spring-boot:run   # terminal 2
```

See [client-demo/README.md](client-demo/README.md) for `curl` walkthroughs of all four patterns.

## Scope

Plaintext gRPC only — no TLS, no retries/deadlines beyond gRPC's defaults, no persistence. This is a protocol-pattern demo, not a production hardening guide. `server-demo` and `client-demo` share the `.proto` contract at build time (`client-demo` points its own codegen at `server-demo/src/main/proto`) but have no Maven dependency on each other — they are independently buildable and deployable.
