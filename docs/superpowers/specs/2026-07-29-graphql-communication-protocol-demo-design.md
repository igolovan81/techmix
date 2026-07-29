# GraphQL Communication Protocol Demo Design

**Date:** 2026-07-29
**Status:** Approved

## Overview

A new `graphql/` module under `communication-protocols/` (sibling to `grpc/`), containing a **single** Spring Boot app — `spring-demo` — built on `spring-boot-starter-graphql`. Unlike gRPC, GraphQL is consumed directly over HTTP/WebSocket by any client, so there is no client/server split: one app hosts the schema, resolvers, and the load tests that exercise it.

The domain is a product catalog (reusing the gRPC demo's 40-product generator for continuity) extended with a `Review` child collection, chosen specifically because a parent-with-children shape is what demonstrates GraphQL's signature behaviors: nested field selection, the N+1 problem, and DataLoader batching as its fix.

No external infrastructure is required (no Docker) — the app runs locally via `mvn spring-boot:run`, like `template-engines/` and `distributed-transactions/saga`.

## Repository structure

```
communication-protocols/
├── README.md                                        (add GraphQL row to the protocol table)
└── graphql/
    ├── README.md                                    (protocol overview: what GraphQL is, the pattern table, running instructions)
    └── spring-demo/
        ├── pom.xml                                  (artifactId: graphql-spring-demo)
        ├── README.md                                (curl + wscat/GraphiQL walkthrough, per-pattern)
        └── src/
            ├── main/
            │   ├── java/com/testingai/graphql/
            │   │   ├── GraphQlSpringDemoApplication.java
            │   │   ├── controller/
            │   │   │   └── DemoController.java       (all @QueryMapping/@MutationMapping/@SubscriptionMapping/@BatchMapping methods)
            │   │   ├── domain/
            │   │   │   ├── Product.java              (record: id, name, priceCents)
            │   │   │   ├── Review.java                (record: id, productId, author, rating, comment)
            │   │   │   ├── ProductCatalogService.java  (in-memory 40-product catalog, same generator as grpc/server-demo's SampleDataService)
            │   │   │   └── ReviewService.java          (in-memory seeded reviews per product; Sinks.Many<Review> event stream for subscriptions)
            │   │   ├── exception/
            │   │   │   └── DemoExceptionResolver.java  (DataFetcherExceptionResolver; classifies FailureSimulator's RuntimeException)
            │   │   └── util/
            │   │       └── FailureSimulator.java       (FAILURE_RATE = 0.05, maybeThrow(String context) — same shape as the Kafka module's)
            │   └── resources/
            │       ├── application.yml                 (server.port: 8092; spring.graphql.graphiql.enabled: true)
            │       └── graphql/
            │           └── schema.graphqls              (see Schema below)
            └── test/
                ├── java/com/testingai/graphql/
                │   ├── GraphQlSpringDemoApplicationTest.java
                │   ├── domain/
                │   │   ├── ProductCatalogServiceTest.java
                │   │   └── ReviewServiceTest.java
                │   ├── exception/
                │   │   └── DemoExceptionResolverTest.java
                │   ├── util/
                │   │   └── FailureSimulatorTest.java
                │   ├── controller/
                │   │   └── DemoIntegrationTest.java      (HttpGraphQlTester against @SpringBootTest(webEnvironment = RANDOM_PORT))
                │   └── performance/
                │       └── DemoSimulation.java            (Gatling; plain HTTP POST of GraphQL query/mutation JSON bodies — Gatling has no GraphQL DSL)
                └── jmeter/
                    └── DemoSimulation.jmx                  (same requests via the jmeter-load-test profile)
```

### Cross-cutting fixes needed in existing files

- **`CLAUDE.md`** — add a "GraphQL communication protocol demo" command section (one app, no client/server split — mirrors the Template engine demos' section shape) and a `communication-protocols/graphql/spring-demo/` row in the repository layout table.
- **`.githooks/pre-commit`** — already greps `^communication-protocols/.*\.java$` and runs `mvn spotless:apply` there (added during the gRPC work); no change needed, this new module is already covered.

## Schema (`spring-demo/src/main/resources/graphql/schema.graphqls`)

```graphql
type Product {
  id: ID!
  name: String!
  priceCents: Int!
  reviews: [Review!]!
}

type Review {
  id: ID!
  productId: ID!
  author: String!
  rating: Int!
  comment: String
}

type Query {
  products: [Product!]!
  product(id: ID!): Product
}

type Mutation {
  addReview(input: AddReviewInput!): Review!
}

type Subscription {
  reviewAdded(productId: ID): Review!
}

input AddReviewInput {
  productId: ID!
  author: String!
  rating: Int!
  comment: String
}
```

## Patterns implemented (parallels gRPC's four-row README table)

| Pattern | Field/operation | What it demonstrates |
|---|---|---|
| Query + nested fetch | `products { reviews { ... } }` | Client asks for exactly the fields it wants, including a nested child collection, in one round trip |
| DataLoader batching | `Product.reviews` via `@BatchMapping` | Solves the N+1 problem: fetching `reviews` for N products in one query triggers **one** batched call to `ReviewService.findByProductIds`, not N — logged with the batch size so it's visible |
| Mutation | `addReview` | A write that returns the created object and publishes it to the subscription stream |
| Subscription | `reviewAdded(productId)` | Real-time push over a GraphQL-over-WebSocket session, optionally filtered server-side by `productId` (omit for all products) |
| Error handling (documented under Query) | `product(id)` — 5% simulated failure via `FailureSimulator` | GraphQL's signature partial-failure behavior: a failing field surfaces in a separate `errors[]` array without failing sibling fields/queries in the same request, unlike an all-or-nothing REST/gRPC call |

## `DemoController`

- **`products()`** — `@QueryMapping`. Returns the full in-memory catalog (40 products) from `ProductCatalogService`.
- **`product(id)`** — `@QueryMapping`. Looks up one product; calls `FailureSimulator.maybeThrow("product query")` first, letting `DemoExceptionResolver` turn a triggered `RuntimeException` into a classified GraphQL error; returns `null`/GraphQL "not found" style for an unknown id (no `NOT_FOUND` exception needed — a `null` `Product` is idiomatic GraphQL for "no result").
- **`reviews(List<Product>)`** — `@BatchMapping`. Calls `ReviewService.findByProductIds(ids)` once per query execution regardless of how many products are being resolved; logs `"batch fetching reviews for {} products in one call"`.
- **`addReview(input)`** — `@MutationMapping`. Validates the referenced product exists (else throws, mapped by `DemoExceptionResolver`), appends the review via `ReviewService`, and publishes it to the `Sinks.Many<Review>` for subscribers.
- **`reviewAdded(productId)`** — `@SubscriptionMapping`. Returns `reviewService.reviewAdded()` (`Flux<Review>`), filtered by `productId` when provided.

`DemoExceptionResolver` implements `DataFetcherExceptionResolver`, mapping the `FailureSimulator`-thrown `RuntimeException` to a `GraphqlErrorBuilder` with `ErrorType.INTERNAL_ERROR`, matching the intent of `grpc/client-demo`'s `DemoExceptionHandler` (classify and shape the error, don't let a raw stack trace leak).

GraphiQL enabled at `/graphiql` (Spring GraphQL's built-in explorer) as this module's equivalent of Swagger UI — no springdoc dependency needed here since there's no REST surface.

## Testing

- **`ProductCatalogServiceTest`** — catalog lookup by id, full listing, same shape as `grpc/server-demo`'s `SampleDataServiceTest`.
- **`ReviewServiceTest`** — seeded reviews present per product, `findByProductIds` batches correctly, `addReview` both stores the review and emits it on the sink (assert via `StepVerifier` on the `Flux`).
- **`FailureSimulatorTest`** — same statistical-rate test as the gRPC/Kafka reference.
- **`DemoExceptionResolverTest`** — asserts a `RuntimeException` resolves to a `GraphQLError` classified as `INTERNAL_ERROR` with the original message preserved.
- **`DemoIntegrationTest`** — `@SpringBootTest(webEnvironment = RANDOM_PORT)` + Spring GraphQL's `HttpGraphQlTester`:
  - query `products` with nested `reviews` — asserts data shape and (via a counter/log-capture on `ReviewService`) that batching occurred once, not N times
  - mutation `addReview` — asserts the returned `Review` and that a subsequent query sees it
  - subscription `reviewAdded` — subscribe, trigger `addReview`, assert the event arrives via `StepVerifier` on the tester's `Flux<Review>`
  - error case — query `product(id)` repeatedly (statistical) or via a test-only forced-failure hook, asserting the response has `errors[]` populated and `data.product: null` for that field while sibling query data in the same request is unaffected
- **`GraphQlSpringDemoApplicationTest`** — context loads, same as `GrpcClientDemoApplicationTest`.
- **`performance/DemoSimulation.java`** — Gatling, excluded from `mvn test` via the inherited surefire `**/performance/**` exclude; POSTs plain JSON bodies (`{"query": "...", "variables": {...}}`) to `/graphql` for the `products` query, `product(id)` query, and `addReview` mutation — 2 users ramped a few seconds apart, matching the gRPC client-demo's pacing style. Subscriptions are excluded from both load tests (WebSocket session lifecycle doesn't fit Gatling's/JMeter's request-response model here, and this demo's load tests exist to be watched in logs, not to measure WS throughput).
- **`src/test/jmeter/DemoSimulation.jmx`** — same three requests, wired behind the `jmeter-load-test` Maven profile exactly as in `grpc/client-demo`.

## Ports

`8092` — next free slot in the `808x` HTTP block after `8091` (grpc/client-demo), `8090` (kafka-ui), `8081`–`8083` (SQS/ASB/Pulsar).

## Spring Boot configuration

**Spring Boot version:** 3.4.4 (inherited from the parent POM)
**Java:** 21

**Dependencies:** `spring-boot-starter-graphql` (includes GraphiQL and WebSocket transport for subscriptions), `spring-boot-starter-web`, `lombok`, `spring-boot-starter-test` (test), `graphql-test` / Spring GraphQL test support (`HttpGraphQlTester`, `reactor-test` for `StepVerifier`), `gatling-charts-highcharts` (test).

`spring.graphql.graphiql.enabled: true` in `application.yml` for the built-in explorer at `/graphiql`.

## README

- `communication-protocols/README.md` — add a GraphQL row to the top-level protocol table (mirroring the gRPC row: "Single Spring Boot app covering query/mutation/subscription/DataLoader patterns") and drop "GraphQL" from the "more protocols to add" sentence.
- `communication-protocols/graphql/README.md` — protocol-level overview: what GraphQL is (schema-first, single endpoint, client-specified field selection) in one paragraph, the pattern table above, running instructions, scope note.
- `communication-protocols/graphql/spring-demo/README.md` — module walkthrough: prerequisites (Java 21, Maven, no Docker), run instructions, `curl` examples for `products`/`product(id)`/`addReview`, a GraphiQL or `wscat` example for `reviewAdded`, the "watching the DataLoader batching in logs" section, and a simulated-failure example (repeat `product(id)` until the 5% `FailureSimulator` trips, showing the `errors[]` response shape), plus build/test commands.

## Scope limits

- No persistence — `ProductCatalogService`/`ReviewService` are in-memory only, matching `template-engines` and `grpc/server-demo`.
- No authentication/authorization, no query complexity/depth limiting, no persisted queries — this is a protocol-pattern demo, not a production-hardening guide, called out explicitly in the module README as a deliberate simplification (same spirit as gRPC's "no TLS" scope limit).
- Subscriptions use an in-memory `Sinks.Many` (multicast, per-app-instance) — no external pub/sub backing, so this is a single-instance demo only.
- One schema, one small domain (`Product`/`Review`) — no attempt to demo GraphQL federation, custom scalars, or directives beyond what's needed to show the four patterns above.
