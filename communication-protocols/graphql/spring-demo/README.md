# GraphQL Spring Demo

Single Spring Boot app exposing a GraphQL schema over `Product`/`Review` data, covering query + nested fetch, DataLoader batching, mutation, and subscription.

## Prerequisites

Java 21, Maven. No Docker.

## Run

```bash
cd communication-protocols
mvn -pl graphql/spring-demo spring-boot:run
```

GraphiQL: http://localhost:8092/graphiql

## Walkthrough

**Query — full catalog:**

```bash
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ products { id name priceCents } }"}'
```

**Query — one product with nested reviews (the DataLoader pattern):**

```bash
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ products { id name reviews { author rating comment } } }"}'
```

Watch the console: even though this fetches reviews for 40 products, `ReviewService` logs `batch fetching reviews for 40 products in one call` exactly once — the whole point of `@BatchMapping`. A naive per-product resolver would instead log (and query) once per product, 40 times.

**Mutation — add a review:**

```bash
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"mutation { addReview(input: { productId: \"p1\", author: \"Jordan\", rating: 5, comment: \"Great product\" }) { id author rating } }"}'
```

**Subscription — watch reviews arrive in real time:**

Open GraphiQL at http://localhost:8092/graphiql, run:

```graphql
subscription {
  reviewAdded(productId: "p1") {
    id
    author
    rating
    comment
  }
}
```

Then, in another tab or via `curl`, run the mutation above (with `productId: "p1"`) — the subscription tab receives the new review immediately. Omit `productId` in the subscription to receive reviews for every product.

**Simulated failure:** the `product(id: ...)` query has a 5% chance of failing (`FailureSimulator`). When it does, the response is still HTTP `200` (GraphQL's convention), but the body's `errors` array carries the failure, `data.product` is `null`, and any other field requested in the same query is unaffected:

```bash
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ products { id } product(id: \"p1\") { id name } }"}'
# repeat a few times to see it trip:
# {"errors":[{"message":"Simulated 5% failure in product query", ...}],"data":{"products":[...40 items...],"product":null}}
```

## Build & test

```bash
mvn clean package                    # build
mvn test                             # unit tests (Gatling excluded automatically)
mvn test -Dtest=ClassName            # single test class
mvn gatling:test                     # Gatling load test — requires the app to be running first
mvn verify -Pjmeter-load-test        # JMeter load test — requires the app to be running first
```

- **Gatling**: `com.testingai.graphql.performance.DemoSimulation` (`src/test/java/.../performance/`). Excluded from `mvn test` automatically; run with `mvn gatling:test`. HTML report under `target/gatling/`.
- **JMeter**: `src/test/jmeter/DemoSimulation.jmx` — open it in the JMeter GUI to inspect or edit it visually, either with a local JMeter install (`jmeter -t src/test/jmeter/DemoSimulation.jmx`) or via the plugin (`mvn jmeter:configure jmeter:gui`). Only wired up behind the `jmeter-load-test` Maven profile, so `mvn clean package`/`mvn verify` without `-Pjmeter-load-test` never touches JMeter. Raw per-sample results (CSV) land in `target/jmeter/results/`; a summary is also printed to the console as the run progresses.

Both load tests drive the same three requests (products+reviews query, product-by-id query, addReview mutation) with the same pacing story — 2 users ramped a few seconds apart, 500ms between calls — designed to be watched in the app's logs rather than to measure throughput. Subscriptions aren't covered by either load test since they're WebSocket sessions, not request/response calls.
