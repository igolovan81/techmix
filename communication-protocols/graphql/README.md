# GraphQL Demo

Demonstrates GraphQL — a schema-first query language for APIs, served over a single endpoint (`/graphql`), where the client specifies exactly which fields it wants — via one Spring Boot app (`spring-demo`) built on `spring-boot-starter-graphql`, against a small Postgres-backed e-commerce domain (Product, Category, Review, User, Order, OrderItem).

Unlike the [gRPC demo](../grpc/), GraphQL doesn't need a client/server split: any HTTP (or WebSocket, for subscriptions) client can talk to the schema directly, so this module is a single app.

Beyond the five patterns below, the domain also demonstrates: DB-pushed-down keyset pagination at 10,000-product scale (alongside the original in-memory cursor pagination, used where a parent's list is always small); `@BatchMapping` used directly wherever no argument is needed, side by side with the manual `DataLoader` registration required when one is; and row-level (not just role-level) authorization on `order(id)`. See [spring-demo/README.md](spring-demo/README.md#domain-model) for the full write-up.

## The five patterns

| Pattern | Field/operation | What it demonstrates |
|---|---|---|
| Query + nested fetch | `products { reviews { ... } }` | Client asks for exactly the fields it wants, including a nested child collection, in one round trip |
| DataLoader batching | `Product.reviews` via a registered `BatchLoaderRegistry` loader | Solves the N+1 problem: fetching `reviews` for N products in one query triggers **one** batched call, not N |
| Mutation | `addReview` | A write that returns the created object and publishes it to the subscription stream |
| Subscription | `reviewAdded(productId)` | Real-time push over a GraphQL-over-WebSocket session, optionally filtered server-side by `productId` |
| Pagination & filtering | `products(filter, first, after)`, `Product.reviews(filter, first, after)` | Relay-style cursor connections (`edges`/`node`/`cursor`/`pageInfo`) plus input-object filtering, at both the root-query and nested level |

### Query + nested fetch

**Pros**
- Client controls the response shape exactly — no over-fetching or under-fetching
- One round trip covers what would otherwise be several REST calls (e.g. product + its reviews)
- Self-documenting: the schema is the contract, browsable via GraphiQL

**Cons**
- Naive nested-field resolution is prone to the N+1 problem (see below)
- Caching a whole response as one unit is harder than with REST's per-URL caching
- Arbitrary client-specified queries can be expensive to compute without query cost limits (out of scope here)

**Typical use cases**
- Aggregating data from multiple related entities in one request
- Clients (mobile, various frontends) that each need a different subset/shape of the same data
- Replacing several REST endpoints with one flexible one

### DataLoader batching

**Pros**
- Solves GraphQL's signature performance pitfall: naively resolving a nested field per-parent (`Product.reviews` for each of N products) is one call each = N+1 total calls
- One batched call for the whole set of parents in a query, regardless of how many there are
- Transparent to the schema/client — no change to the query shape, just to how the server resolves it

**Cons**
- Requires deliberate implementation (`@BatchMapping` in Spring GraphQL, or a manually registered `BatchLoaderRegistry` loader plus a `@SchemaMapping` resolver — this demo uses the latter for `Product.reviews`, since `@BatchMapping` methods can't accept `@Argument` parameters and this field needs `filter`/`first`/`after`) — nothing prevents writing the naive N+1 version by mistake
- Batching only helps within a single query execution; it doesn't cache across separate requests

**Typical use cases**
- Any one-to-many nested field resolved from a different data source than its parent (reviews per product, comments per post, orders per customer)
- Anywhere a REST API would separately need "include" or "expand" query parameters

### Mutation

**Pros**
- Same request/response ergonomics as a query — client still specifies which fields of the result it wants back
- Explicit separation from queries makes read/write intent unambiguous in the schema

**Cons**
- No built-in idempotency or optimistic-concurrency story — same as a REST POST, this is left to the application
- A single mutation is one write; batching multiple writes into one round trip needs a custom input shape (e.g. a list input), not built into the spec

**Typical use cases**
- Any create/update/delete operation
- Actions that should return the resulting object shaped by client-specified fields (e.g. return the id and computed fields right after creating something)

### Subscription

**Pros**
- Real-time push without polling, over a single persistent connection (WebSocket)
- Same field-selection ergonomics as queries — the client only receives the fields it asked for
- Can be filtered server-side per subscription (e.g. one client only wants updates for one product)

**Cons**
- Needs a stateful, persistent connection — different operational story than plain request/response (reconnect/backoff, connection limits)
- This demo's event stream is in-memory (`Sinks.Many`), so it's single-instance only; a multi-instance deployment needs an external pub/sub backing it
- Harder to test and debug than request/response patterns

**Typical use cases**
- Live updates: new reviews/comments, order status changes, notifications
- Dashboards and UIs that should reflect server-side changes without polling

### Pagination & filtering

**Pros**
- `edges`/`node`/`cursor`/`pageInfo` is the GraphQL-idiomatic pagination shape (Relay connections) — a client can page forward and know whether more data exists without a separate count call
- Input-object filters (`ProductFilter`, `ReviewFilter`) keep filtering criteria typed and self-documenting in the schema, instead of ad-hoc string query parameters
- The same generic connection wrapper works for both a root query (`products`) and a nested field (`Product.reviews`)

**Cons**
- More verbose response shape than a bare list — every item is wrapped in an `edges { node { ... } }` layer
- This demo's cursors encode a plain list position, not an opaque, storage-independent key — fine for in-memory data, not representative of a cursor scheme robust to underlying reordering
- A cursor issued under one filter isn't guaranteed meaningful against a different filter — callers are expected to page within one filter, not mix cursors across filters

**Typical use cases**
- Any list endpoint large enough that returning everything in one response is wasteful (product catalogs, comment threads, activity feeds)
- APIs where clients need to narrow a large collection by one or more criteria before paging through it

## Running the demo

Docker is needed to run the app (Postgres) — but not for `mvn test`, which runs against an embedded H2 database.

```bash
docker compose -f docker/docker-compose.yml up -d   # Postgres :5433

cd communication-protocols
mvn -pl graphql/spring-demo spring-boot:run
```

GraphiQL (interactive schema explorer): http://localhost:8092/graphiql

See [spring-demo/README.md](spring-demo/README.md) for `curl` and subscription walkthroughs of all five patterns, plus the [domain model](spring-demo/README.md#domain-model) write-up.

An Angular browser client tours the same five patterns interactively — see [angular-demo/README.md](angular-demo/README.md).

## Scope

No query depth/complexity limiting, no persisted queries, no GraphQL federation — this is a protocol-pattern demo, not a production-hardening guide (same spirit as the gRPC demo's "no TLS" scope limit). Subscriptions are backed by a single in-process `Sinks.Many`, so this is a single-instance demo only. Authentication is HTTP Basic against in-memory demo credentials, not a production auth story — see [spring-demo/README.md#security](spring-demo/README.md#security).
