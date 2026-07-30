# GraphQL Spring Demo

Single Spring Boot app exposing a GraphQL schema over `Product`/`Review` data, covering query + nested fetch, DataLoader batching, mutation, subscription, and Relay-style cursor pagination with filtering.

## Prerequisites

Java 21, Maven. No Docker.

## Run

```bash
cd communication-protocols
mvn -pl graphql/spring-demo spring-boot:run
```

GraphiQL: http://localhost:8092/graphiql

## Walkthrough

**Query — first page of the catalog (10 by default):**

```bash
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ products { edges { node { id name priceCents } cursor } pageInfo { hasNextPage endCursor } totalCount } }"}'
```

**Query — one product with nested reviews (the DataLoader pattern):**

```bash
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ products(first: 40) { edges { node { id name reviews { edges { node { author rating comment } } } } } } }"}'
```

Watch the console: even though this fetches reviews for 40 products, `ReviewService` logs `batch fetching reviews for 40 products in one call` exactly once — the whole point of the DataLoader backing `Product.reviews`. A naive per-product resolver would instead log (and query) once per product, 40 times.

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
  -d '{"query":"{ products { edges { node { id } } } product(id: \"p1\") { id name } }"}'
# repeat a few times to see it trip:
# {"errors":[{"message":"Simulated 5% failure in product query", ...}],"data":{"products":{"edges":[...10 items...]},"product":null}}
```

## Pagination & filtering

`products` and `Product.reviews` both use Relay-style cursor connections (`edges`/`node`/`cursor`/`pageInfo`) instead of bare lists — `first` defaults to 10 and is clamped to 50; pagination is forward-only (`first`/`after`, no `last`/`before`).

**Page through the catalog:**

```bash
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ products(first: 5) { edges { node { name } cursor } pageInfo { hasNextPage endCursor } } }"}'

# feed the previous response's pageInfo.endCursor in as "after" for the next page:
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ products(first: 5, after: \"<endCursor from previous response>\") { edges { node { name } } pageInfo { hasNextPage } } }"}'
```

**Filter products by name and price range:**

```bash
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ products(filter: { nameContains: \"widget\", minPriceCents: 1000, maxPriceCents: 3000 }, first: 10) { edges { node { name priceCents } } totalCount } }"}'
# {"data":{"products":{"edges":[{"node":{"name":"Standard Widget","priceCents":2006}}],"totalCount":1}}}
```

**Filter reviews by minimum rating** (add a qualifying review first — `p1`'s seeded review is rating 3, below the filter):

```bash
curl -s -u user:userPassword http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"mutation { addReview(input: { productId: \"p1\", author: \"Jordan\", rating: 5, comment: \"Great product\" }) { id } }"}'

curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ product(id: \"p1\") { reviews(filter: { minRating: 4 }) { edges { node { author rating } } } } }"}'
# {"data":{"product":{"reviews":{"edges":[{"node":{"author":"Jordan","rating":5}}]}}}}
```

**Caveat:** a cursor encodes a position in the filtered, ordered list it was issued from — reusing a cursor from one `filter` against a different `filter` (or against no filter at all) returns whatever that position happens to be in the new list, not the same logical page.

## Security

Three operations are gated by HTTP Basic auth and Spring Security method security (`@PreAuthorize` on `DemoController`), demonstrating that GraphQL authorization is field-level, not URL-level — there's only one endpoint (`/graphql`) for every operation:

| Operation | Rule |
|---|---|
| `products`, `product(id)` | Public — no annotation |
| `addReview`, `reviewAdded` (subscription) | `isAuthenticated()` — any of the two demo users |
| `deleteReview(id)` | `hasRole('ADMIN')` — the one action where the two demo users behave differently |

Demo users (same credentials as `backend/rest-api`, in-memory, not for production use): `user`/`userPassword` (ROLE_USER), `admin`/`adminPassword` (ROLE_ADMIN).

**Anonymous — public query still works:**

```bash
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ products { edges { node { id } } } }"}'
```

**Anonymous — protected mutation is rejected:**

```bash
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"mutation { addReview(input: { productId: \"p1\", author: \"a\", rating: 5, comment: \"c\" }) { id } }"}'
# {"errors":[{"message":"Access Denied", "extensions":{"classification":"UNAUTHORIZED"}}, ...],"data":null}
```

**USER — allowed to add a review, forbidden from deleting one:**

```bash
curl -s -u user:userPassword http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"mutation { addReview(input: { productId: \"p1\", author: \"Jordan\", rating: 5, comment: \"Great product\" }) { id author } }"}'

curl -s -u user:userPassword http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"mutation { deleteReview(id: \"some-review-id\") }"}'
# {"errors":[{"message":"Access Denied", "extensions":{"classification":"FORBIDDEN"}}],"data":{"deleteReview":null}}
```

**ADMIN — allowed to delete a review:**

```bash
curl -s -u admin:adminPassword http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"mutation { deleteReview(id: \"some-review-id\") }"}'
# {"data":{"deleteReview":true}}
```

**Scope limits:**

- Demo credentials only — HTTP Basic, plaintext (`{noop}`-prefixed) in-memory users, matching this repo's "local demo" scope everywhere else. Not a production security guide: no JWT/OAuth2/OIDC, no real password encoding, no per-user data ownership (roles gate *actions*, not *which data a user can see*).
- CSRF is disabled in `SecurityConfig` — matches `backend/rest-api`, and is irrelevant here since this is a stateless Basic-auth API with no cookie-based session.
- Subscription-establishment authorization failures don't go through the same error classification as query/mutation fields — an unauthenticated `reviewAdded` subscription attempt fails with a generic transport-level error (`SubscriptionErrorException`, classified `INTERNAL_ERROR`) rather than `UNAUTHORIZED`. This is a Spring GraphQL limitation for this failure mode, not something this demo works around.

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

Both load tests drive the same three requests (products+reviews connection query, product-by-id query, addReview mutation) with the same pacing story — 2 users ramped a few seconds apart, 500ms between calls — designed to be watched in the app's logs rather than to measure throughput. Subscriptions aren't covered by either load test since they're WebSocket sessions, not request/response calls.
