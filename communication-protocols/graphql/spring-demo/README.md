# GraphQL Spring Demo

Single Spring Boot app exposing a GraphQL schema over a small Postgres-backed e-commerce domain (Product, Category, Review, User, Order, OrderItem), covering query + nested fetch, DataLoader batching (both `@BatchMapping` and manual registration), mutation, subscription, Relay-style cursor pagination (both DB-pushed-down and in-memory), field-level security including row-level authorization, a transactional multi-row mutation, and a REST-sidecar pattern for binary file upload/download.

## Prerequisites

Java 21, Maven. Docker is needed to *run* the app (Postgres) — but not for `mvn test`, which runs against an embedded H2 database in Postgres-compatibility mode.

## Run

```bash
docker compose -f ../docker/docker-compose.yml up -d   # Postgres :5433 — or from the repo root:
# docker compose -f communication-protocols/graphql/docker/docker-compose.yml up -d

cd communication-protocols
mvn -pl graphql/spring-demo spring-boot:run
```

GraphiQL: http://localhost:8092/graphiql

The app seeds itself on first startup (skipped on later restarts once data exists): 100 users, 100 categories, 10,000 products, 3–10 reviews per product, ~3,000 orders. Volumes are configurable via `app.seed.*` in `application.yml`.

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
  -d '{"query":"{ products(first: 40) { edges { node { id name reviews { edges { node { author { username } rating comment } } } } } } }"}'
```

Watch the console: even though this fetches reviews for 40 products, `ReviewService` logs `batch fetching reviews for 40 products in one call` exactly once — the whole point of the DataLoader backing `Product.reviews`. A naive per-product resolver would instead log (and query) once per product, 40 times.

**Mutation — add a review** (requires auth — see [Security](#security) below; the author is resolved from the authenticated principal, not passed in):

```bash
curl -s -u user:userPassword http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"mutation { addReview(input: { productId: \"1\", rating: 5, comment: \"Great product\" }) { id author { username } rating } }"}'
```

Product ids are now database-assigned integers (seeded starting at `1`), not the old `p1`/`p2`/... scheme — swap in whatever id a `products` query on your running instance actually returns.

**Subscription — watch reviews arrive in real time:**

Open GraphiQL at http://localhost:8092/graphiql, run:

```graphql
subscription {
  reviewAdded(productId: "1") {
    id
    author { username }
    rating
    comment
  }
}
```

Then, in another tab or via `curl`, run the mutation above (with the same `productId`) — the subscription tab receives the new review immediately. Omit `productId` in the subscription to receive reviews for every product.

**Simulated failure:** the `product(id: ...)` query has a 5% chance of failing (`FailureSimulator`). When it does, the response is still HTTP `200` (GraphQL's convention), but the body's `errors` array carries the failure, `data.product` is `null`, and any other field requested in the same query is unaffected:

```bash
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ products { edges { node { id } } } product(id: \"1\") { id name } }"}'
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
# product names/prices are randomly seeded (DemoDataSeeder), so exact matches vary run to run, e.g.:
# {"data":{"products":{"edges":[{"node":{"name":"Standard Widget #42","priceCents":2006}}],"totalCount":3}}}
```

**Filter reviews by minimum rating** (seeded reviews have random ratings 1–5, so add a qualifying one to guarantee a hit):

```bash
curl -s -u user:userPassword http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"mutation { addReview(input: { productId: \"1\", rating: 5, comment: \"Great product\" }) { id } }"}'

curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ product(id: \"1\") { reviews(filter: { minRating: 4 }) { edges { node { author { username } rating } } } } }"}'
# {"data":{"product":{"reviews":{"edges":[{"node":{"author":{"username":"user"},"rating":5}}]}}}}
```

**Caveat:** a cursor encodes a position in the filtered, ordered list it was issued from — reusing a cursor from one `filter` against a different `filter` (or against no filter at all) returns whatever that position happens to be in the new list, not the same logical page.

## File upload/download

`Product.imageUrl` (nullable) points at a REST endpoint, not a GraphQL field — GraphQL has no way to carry binary payloads, so the schema only ever exposes the pointer:

```bash
# upload (ADMIN only) — replace <path-to-image> with a real image file
curl -s -u admin:adminPassword -F "file=@<path-to-image>;type=image/png" \
  http://localhost:8092/api/products/1/image
# 204 No Content on success

# the schema field now resolves to the download path:
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ product(id: \"1\") { imageUrl } }"}'
# {"data":{"product":{"imageUrl":"/api/products/1/image"}}}

# download (public, no auth needed)
curl -s http://localhost:8092/api/products/1/image -o downloaded-image.png
```

Uploading again for the same product replaces the previous image (one image per product, no gallery). Uploads are capped at 5MB and must have an `image/*` content type; both are rejected with `400`/`413` respectively, and uploading to an unknown product id returns `404`.

## Security

Several operations are gated by HTTP Basic auth and Spring Security method security (`@PreAuthorize` on `DemoController`), demonstrating that GraphQL authorization is field-level, not URL-level — there's only one endpoint (`/graphql`) for every operation:

| Operation | Rule |
|---|---|
| `products`, `product(id)`, `categories`, `category(id)` | Public — no annotation |
| `addReview`, `reviewAdded` (subscription), `me`, `placeOrder` | `isAuthenticated()` — any of the two demo users |
| `deleteReview(id)`, `orders`, `updateOrderStatus` | `hasRole('ADMIN')` |
| `order(id)` | `isAuthenticated()`, **plus row-level ownership** — the caller must be the order's owner or an ADMIN, checked against the loaded row rather than a static rule (see [Domain model](#domain-model)) |

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
  -d '{"query":"mutation { addReview(input: { productId: \"1\", rating: 5, comment: \"c\" }) { id } }"}'
# {"errors":[{"message":"Access Denied", "extensions":{"classification":"UNAUTHORIZED"}}, ...],"data":null}
```

**USER — allowed to add a review, forbidden from deleting one:**

```bash
curl -s -u user:userPassword http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"mutation { addReview(input: { productId: \"1\", rating: 5, comment: \"Great product\" }) { id author { username } } }"}'

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

- Demo credentials only — HTTP Basic, plaintext (`{noop}`-prefixed) in-memory users, matching this repo's "local demo" scope everywhere else. Not a production security guide: no JWT/OAuth2/OIDC, no real password encoding. Most operations here still gate *actions* by role only, not *which data a user can see* — `order(id)` is the one exception; see [Domain model](#domain-model) below.
- CSRF is disabled in `SecurityConfig` — matches `backend/rest-api`, and is irrelevant here since this is a stateless Basic-auth API with no cookie-based session.
- Subscription-establishment authorization failures don't go through the same error classification as query/mutation fields — an unauthenticated `reviewAdded` subscription attempt fails with a generic transport-level error (`SubscriptionErrorException`, classified `INTERNAL_ERROR`) rather than `UNAUTHORIZED`. This is a Spring GraphQL limitation for this failure mode, not something this demo works around.

## Domain model

Six entities, Postgres-backed via Liquibase + Spring Data JPA (`entity`/`repository` packages), mapped to plain GraphQL-facing records (`domain` package) the same way `Product`/`Review` already worked before this domain was extended:

- **User** — `username`/`displayName`/`role` (`CUSTOMER`/`ADMIN`); `username` matches the Basic-Auth demo accounts (`user`/`admin`) so the authenticated principal resolves directly to a domain `User`.
- **Category** — a self-referencing tree (`parent`/`children`) with a many-to-many relation to `Product`.
- **Product** — `name`/`priceCents` plus `stockQty` and `categories`.
- **Review** — `author` is a full `User` (not a free-text string).
- **Order** / **OrderItem** — `placeOrder` creates both in one transaction; `OrderItem.unitPriceCents` is a snapshot of the product's price at order time, not a live join, so historical orders don't change value if a price changes later.

Seeded on every startup (idempotent — skipped once data exists): 100 users, 100 categories, 10,000 products, 3–10 reviews per product, ~3,000 orders by default (`app.seed.*` in `application.yml`; much smaller in the test profile so `mvn test` stays fast and Docker-free).

**Pagination: two strategies, chosen by scale.** `products`, `Category.products`, and the admin `orders` query push pagination down to the database (keyset: the cursor encodes the last-seen row id, `WHERE id > :cursorId ORDER BY id LIMIT :n`) — these can legitimately span the whole 10k-row table. `Category.children`, `Product.reviews`, and `User.orders` keep the original in-memory `CursorPagination` (full list loaded per parent, sliced afterward) — each parent's list is inherently small (at most tens of rows) regardless of overall table size, so pushing those down too would gain nothing.

**`@BatchMapping` vs. manual `DataLoader`:**

| Field | Mechanism | Why |
|---|---|---|
| `Product.categories`, `Category.parent`, `Review.author`, `Order.user`, `OrderItem.product`, `Order.items` | `@BatchMapping` | No `@Argument` needed — Spring GraphQL batches these with zero manual registration |
| `Product.reviews`, `Category.children`, `User.orders` | Manual `BatchLoaderRegistry` | Need `@Argument` (filter/pagination), which `@BatchMapping` methods can't accept |
| `Category.products` | Neither — a direct per-node keyset query | Batching it the way `reviews` works would mean loading a category's *entire* unpaginated product list per key just to slice it afterward, defeating the DB-pushdown pagination above |

**Row-level authorization.** `order(id)` is the one place in this demo where authorization depends on the data, not just the caller's role: the resolver loads the order, then allows it only if the caller is the owning user or an `ADMIN`, throwing `AccessDeniedException` (classified `FORBIDDEN`/`UNAUTHORIZED` by the existing `DemoExceptionResolver`, no changes needed there) otherwise. Try it as the `user` account against an order placed by `admin` (and vice versa) to see the distinction from `deleteReview`'s plain role check.

**`placeOrder` correctness.** Runs in a single transaction: each product row is fetched with a pessimistic write lock (`SELECT ... FOR UPDATE`) so two concurrent orders against the same product can't both oversell it, stock is validated and decremented per line, and `unitPriceCents` is snapshotted from the product's current price. If any line fails (insufficient stock, unknown product), the whole order — including any earlier lines already decremented in the same call — rolls back.

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
