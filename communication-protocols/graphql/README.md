# GraphQL Demo

Demonstrates GraphQL — a schema-first query language for APIs, served over a single endpoint (`/graphql`), where the client specifies exactly which fields it wants — via one Spring Boot app (`spring-demo`) built on `spring-boot-starter-graphql`, against a Products↔Reviews domain.

Unlike the [gRPC demo](../grpc/), GraphQL doesn't need a client/server split: any HTTP (or WebSocket, for subscriptions) client can talk to the schema directly, so this module is a single app.

## The four patterns

| Pattern | Field/operation | What it demonstrates |
|---|---|---|
| Query + nested fetch | `products { reviews { ... } }` | Client asks for exactly the fields it wants, including a nested child collection, in one round trip |
| DataLoader batching | `Product.reviews` via `@BatchMapping` | Solves the N+1 problem: fetching `reviews` for N products in one query triggers **one** batched call, not N |
| Mutation | `addReview` | A write that returns the created object and publishes it to the subscription stream |
| Subscription | `reviewAdded(productId)` | Real-time push over a GraphQL-over-WebSocket session, optionally filtered server-side by `productId` |

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
- Requires deliberate implementation (`@BatchMapping` in Spring GraphQL, or a `DataLoader` registry in vanilla graphql-java) — nothing prevents writing the naive N+1 version by mistake
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

## Running the demo

No Docker required — everything is in-memory.

```bash
cd communication-protocols
mvn -pl graphql/spring-demo spring-boot:run
```

GraphiQL (interactive schema explorer): http://localhost:8092/graphiql

See [spring-demo/README.md](spring-demo/README.md) for `curl` and subscription walkthroughs of all four patterns.

## Scope

In-memory data only, no persistence, no authentication/authorization, no query depth/complexity limiting, no persisted queries, no GraphQL federation — this is a protocol-pattern demo, not a production-hardening guide (same spirit as the gRPC demo's "no TLS" scope limit). Subscriptions are backed by a single in-process `Sinks.Many`, so this is a single-instance demo only.
