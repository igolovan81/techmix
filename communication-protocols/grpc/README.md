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

### Unary — `GetProduct`

**Pros:** simplest pattern to reason about; maps 1:1 onto REST-style thinking; per-call timeouts/retries/load balancing work exactly as expected; supported uniformly by every gRPC tool and proxy.
**Cons:** one full round trip per request — no better than REST for high-frequency small calls; no way to start acting on a large result before it's fully assembled; doesn't amortize connection/setup cost for bulk data.
**Typical use cases:** point lookups and CRUD-style calls ("get user by id", "validate a token"), commands with a single well-defined result, anywhere a REST endpoint would normally be reached for.

### Server streaming — `ListProducts`

**Pros:** server can start sending results before the full set is computed, lowering time-to-first-byte; avoids buffering a huge response in memory on either side; client can process results incrementally as they arrive.
**Cons:** client code is more complex than handling a single object (must consume a stream); the whole response can't be cached as one unit; a mid-stream error leaves the client with a partial result it must handle explicitly.
**Typical use cases:** large or open-ended result sets and exports, paginated data delivered as one call instead of many, live feeds pushed from the server (log tailing, price ticks), search results streamed back as they're found.

### Client streaming — `UploadOrders`

**Pros:** client sends data incrementally instead of buffering an entire payload locally first; server can validate/process each item as it arrives, catching problems early; still only one round trip for the final response.
**Cons:** the client gets no feedback until the very end unless the app defines its own progress signal; the server must pick a buffering/aggregation strategy; retrying a partially-sent stream after a failure is harder than retrying a single unary call.
**Typical use cases:** bulk uploads and batch inserts, ingesting logs/metrics/sensor readings as they're produced, chunked file uploads, any workload where the client naturally produces items over time and only needs one summary back.

### Bidirectional streaming — `StreamOrderStatus`

**Pros:** full duplex — client and server each push messages independently and asynchronously over a single long-lived connection; lowest latency for a continuous two-way interaction; avoids the overhead of opening a new call per message.
**Cons:** the most complex pattern to implement, test, and debug (concurrent send/receive and flow control on both sides); connection-oriented, so load balancing and connection pooling need deliberate design; proxies/gateways with weak HTTP/2 streaming support can behave poorly with it.
**Typical use cases:** chat and real-time collaboration features, live bidirectional sync (order tracking, multiplayer state), long-lived control channels such as IoT device command-and-telemetry links.

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
