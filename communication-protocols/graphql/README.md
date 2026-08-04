# GraphQL Demo

Demonstrates GraphQL — a schema-first query language for APIs, served over a single endpoint (`/graphql`), where the client specifies exactly which fields it wants — via one Spring Boot app (`spring-demo`) built on `spring-boot-starter-graphql`, against a small Postgres-backed e-commerce domain (Product, Category, Review, User, Order, OrderItem).

Unlike the [gRPC demo](../grpc/), GraphQL doesn't need a client/server split: any HTTP (or WebSocket, for subscriptions) client can talk to the schema directly, so this module is a single app.

Beyond the six patterns below, the domain also demonstrates: DB-pushed-down keyset pagination at 10,000-product scale (alongside the original in-memory cursor pagination, used where a parent's list is always small); `@BatchMapping` used directly wherever no argument is needed, side by side with the manual `DataLoader` registration required when one is; and row-level (not just role-level) authorization on `order(id)`. See [spring-demo/README.md](spring-demo/README.md#domain-model) for the full write-up.

## The six patterns

| Pattern | Field/operation | What it demonstrates |
|---|---|---|
| Query + nested fetch | `products { reviews { ... } }` | Client asks for exactly the fields it wants, including a nested child collection, in one round trip |
| DataLoader batching | `Product.reviews` via a registered `BatchLoaderRegistry` loader | Solves the N+1 problem: fetching `reviews` for N products in one query triggers **one** batched call, not N |
| Mutation | `addReview` | A write that returns the created object and publishes it to the subscription stream |
| Subscription | `reviewAdded(productId)` | Real-time push over a GraphQL-over-WebSocket session, optionally filtered server-side by `productId` |
| Pagination & filtering | `products(filter, first, after)`, `Product.reviews(filter, first, after)` | Relay-style cursor connections (`edges`/`node`/`cursor`/`pageInfo`) plus input-object filtering, at both the root-query and nested level |
| File upload/download | `Product.imageUrl` + REST sidecar (`POST`/`GET /api/products/{id}/image`) | GraphQL carries no binary payloads — the schema exposes only a pointer field, and the bytes move over a plain REST endpoint next to `/graphql` |

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

### File upload/download

**Pros**
- Keeps large binary payloads off the GraphQL execution engine entirely — no query-cost or streaming complications for the schema to deal with
- The REST endpoint can be fronted by ordinary HTTP caching/CDN infrastructure, unlike a POST-only GraphQL response
- `Product.imageUrl` still fits normal field-selection ergonomics: a client that doesn't ask for it never gets an extra round trip, and the field is resolved batched (no N+1) exactly like `Product.categories`

**Cons**
- Two request lifecycles for one logical resource (schema pointer + REST fetch) instead of one
- Authorization has to be enforced twice, independently — once for the GraphQL field (implicitly, via whatever gates a `Product` query) and once for the REST endpoint (`@PreAuthorize` on `ProductImageController`) — nothing ties them together automatically
- Not part of the GraphQL spec at all, so this pattern (unlike the other five) has no schema-level standardization; every API does it slightly differently

**Typical use cases**
- Any binary attachment on a GraphQL-modeled entity: avatars, product images, PDF exports, generated reports
- APIs that want to keep binary transfer cacheable/CDN-friendly while still describing the rest of the domain in GraphQL

## Caching

`Category` reads are cached — the only entity in this schema no resolver ever mutates (no `addCategory`/`updateCategory`/`deleteCategory` mutation exists), which makes it a clean cache-aside example with no invalidation logic. Caffeine, in-process, 500-entry cap, 5-minute TTL (a safety net for out-of-band data changes, not a response to any write path in this app).

**Pros**
- Repeated reads of the same category (or its children) skip the database entirely after the first load
- No invalidation complexity to get wrong, because nothing in the schema writes to this entity
- Demonstrates two idioms side by side: plain `@Cacheable` for the single-key `category(id)` lookup, and manual cache-aside for the two batch methods behind `Category.children`'s `DataLoader` and `Category.parent`'s `@BatchMapping` — annotating a batch method with `@Cacheable` would cache by the whole incoming id list as one key, which almost never repeats across requests

**Cons**
- Only safe because this entity happens to be read-only from the app's perspective; caching a mutated entity (`Product.stockQty`, `Review`) would need `@CacheEvict` wired into the write path, a different (and harder) lesson not covered here
- Single-process cache (Caffeine, not Redis) — a multi-instance deployment would see cache misses diverge per instance, same single-instance caveat as this demo's in-memory subscription stream
- The 5-minute TTL is a fixed constant, not exercised by an automated test — verified instead by watching the `cache miss` log lines (`com.testingai.graphql.domain.CategoryService`) appear once per id, then stop

**Typical use cases**
- Reference/lookup data that changes rarely or never from the application's own perspective (category trees, country/currency lists, feature flags)
- Any read hot path where the mutation surface is well understood and provably absent, so "no eviction needed" is a fact, not an assumption

## Running the demo

Docker is needed to run the app (Postgres) — but not for `mvn test`, which runs against an embedded H2 database.

```bash
docker compose -f docker/docker-compose.yml up -d   # Postgres :5433

cd communication-protocols
mvn -pl graphql/spring-demo spring-boot:run
```

GraphiQL (interactive schema explorer): http://localhost:8092/graphiql

See [spring-demo/README.md](spring-demo/README.md) for `curl` and subscription walkthroughs of all six patterns, plus the [domain model](spring-demo/README.md#domain-model) write-up.

An Angular browser client tours the same six patterns interactively — see [angular-demo/README.md](angular-demo/README.md).

## Scope

No query depth/complexity limiting, no persisted queries, no GraphQL federation — this is a protocol-pattern demo, not a production-hardening guide (same spirit as the gRPC demo's "no TLS" scope limit). Subscriptions are backed by a single in-process `Sinks.Many`, so this is a single-instance demo only. Authentication is HTTP Basic against in-memory demo credentials, not a production auth story — see [spring-demo/README.md#security](spring-demo/README.md#security). File transfer is intentionally a REST sidecar (`Product.imageUrl` + `/api/products/{id}/image`), not the `graphql-multipart-request-spec` extension — GraphQL has no native binary support, and this keeps the schema's "single endpoint for everything" story honest about where it does and doesn't apply.
