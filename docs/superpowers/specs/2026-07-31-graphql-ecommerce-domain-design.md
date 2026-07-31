# GraphQL Demo — E-Commerce Domain Extension Design

**Date:** 2026-07-31
**Status:** Proposed
**Builds on:** `docs/superpowers/specs/2026-07-29-graphql-communication-protocol-demo-design.md` (original GraphQL demo — query/DataLoader/mutation/subscription patterns), `docs/superpowers/specs/2026-07-30-graphql-pagination-filtering-design.md` (cursor pagination/filtering addendum), `docs/superpowers/specs/2026-07-30-graphql-security-roles-design.md` (security/roles addendum)

## Overview

Extends `communication-protocols/graphql/spring-demo`'s domain from Products/Reviews to a fuller e-commerce model: **Category**, **User**, **Order**, **OrderItem**. The goal is not just a believable catalog — it's broader GraphQL pattern coverage that the existing four entities' scale (40 products, in-memory `List`/`Map`) can't exercise: real persistence, a self-referencing tree, a many-to-many relation, `@BatchMapping` vs. manual `DataLoader` registration side by side, DB-pushed-down cursor pagination at scale, row-level (not just role-level) authorization, and a multi-row transactional mutation.

This is the first change to introduce a real database into this module. Product and Review move from `ProductCatalogService`'s hand-built in-memory catalog to Postgres-backed JPA entities; the GraphQL-facing types stay plain records, mapped by services, so the existing `Product`/`Review` record contracts and the DataLoader-per-relation pattern established in the original design carry forward unchanged in spirit.

**Scale:** 100 users, 100 categories, 10,000 products, 3–10 reviews per product (~35–65k reviews), a few thousand orders — large enough that naive full-table-load pagination (today's `CursorPagination` behavior) would be wrong, which is precisely why this design introduces DB-level keyset pagination for the listings that need it.

## Architecture decision

Three options were considered for how the new persistence layer relates to the GraphQL surface:

1. **JPA entities double as GraphQL types** (like `backend/rest-api`'s `Post`) — least code, but mixes mutable, proxy-backed, lazily-associated JPA objects into GraphQL resolution, a classic source of `LazyInitializationException` and accidental N+1.
2. **JPA entities for persistence, plain records for the GraphQL surface** — keeps today's shape: immutable records returned to GraphQL, backed by `@Entity` classes + Spring Data repositories, mapped by services exactly as `ProductCatalogService`/`ReviewService` already do (just from a DB instead of a seeded `List`). Every relation gets its own DataLoader batch query, consistent with the existing `Product.reviews` pattern.
3. **Entities exposed via Spring Data projections** — less mapping code than (2), but projections are a Spring Data-specific trick, not a GraphQL pattern, and would teach the wrong lesson for a module whose purpose is GraphQL pattern coverage.

**Chosen: (2).** Minimal-surprise extension of the existing architecture; maximizes genuine GraphQL pattern coverage instead of introducing an unrelated persistence idiom as "the interesting part."

## Relational schema

Postgres 15, managed by Liquibase. New changelog files under `communication-protocols/graphql/spring-demo/src/main/resources/db/changelog/`, referenced from `db.changelog-master.xml`, one table per file (naming convention matches `backend/rest-api`: `db.changelog-1-create-users-table.xml`, `-2-create-categories-table.xml`, `-3-create-products-table.xml`, `-4-create-product-categories-table.xml`, `-5-create-reviews-table.xml`, `-6-create-orders-table.xml`, `-7-create-order-items-table.xml`). No data-insert changelogs — seeding is code (see below), not migration data.

```
users
  id            BIGINT PK
  username      VARCHAR UNIQUE NOT NULL   -- matches Spring Security's "user"/"admin" demo accounts
  email         VARCHAR NOT NULL
  display_name  VARCHAR NOT NULL
  role          VARCHAR NOT NULL          -- CUSTOMER | ADMIN

categories
  id            BIGINT PK
  name          VARCHAR NOT NULL
  parent_id     BIGINT NULL FK -> categories.id     -- self-referencing tree, NULL = root

product_categories                         -- join table, many-to-many
  product_id    BIGINT FK -> products.id
  category_id   BIGINT FK -> categories.id
  PK (product_id, category_id)

products                                   -- migrated from in-memory seed
  id            BIGINT PK
  name          VARCHAR NOT NULL
  price_cents   BIGINT NOT NULL
  stock_qty     INT NOT NULL               -- new: enables order fulfillment validation

reviews                                    -- migrated; author (String) -> author_id FK
  id            UUID PK
  product_id    BIGINT FK -> products.id
  author_id     BIGINT FK -> users.id
  rating        INT NOT NULL
  comment       VARCHAR

orders
  id            BIGINT PK
  user_id       BIGINT FK -> users.id
  status        VARCHAR NOT NULL           -- PENDING | PAID | SHIPPED | DELIVERED | CANCELLED
  placed_at     TIMESTAMP NOT NULL

order_items
  id                BIGINT PK
  order_id          BIGINT FK -> orders.id
  product_id        BIGINT FK -> products.id
  quantity          INT NOT NULL
  unit_price_cents  BIGINT NOT NULL        -- snapshot of Product.priceCents at order time
```

`order_items.unit_price_cents` is a snapshot, not a live join to `products.price_cents` — a historical order's value must not change if a product's price changes later.

## Infrastructure

New `communication-protocols/graphql/docker/docker-compose.yml`, following the per-module convention used by `message-brokers/*`: Postgres 15, its own container/port (`:5433`, avoiding the shared stack's Postgres on `:5432`), db `graphqldemo`. Started with `docker compose -f communication-protocols/graphql/docker/docker-compose.yml up -d` before `mvn -pl graphql/spring-demo spring-boot:run`, added to the module's command block in the root `CLAUDE.md`.

**Test database:** H2 in Postgres-compatibility mode (`jdbc:h2:mem:...;MODE=PostgreSQL`) for `mvn test`, real Postgres only for the running app — consistent with the H2-for-tests/Postgres-for-prod split `CLAUDE.md` already documents for `backend/rest-api`. Keeps `mvn test` Docker-free, preserving this module's existing "no external infrastructure required to build" contract even though running the app now needs Docker.

## Seeding

An `ApplicationRunner` (`DemoDataSeeder`), guarded by `if (userRepository.count() == 0)` so it only ever runs once against an empty database. Uses a fixed-seed `java.util.Random(42)` for reproducibility — deterministic output makes assertions about seeded data (e.g. "product p1 has between 3 and 10 reviews") possible in tests.

Volumes are `application.yml` properties, not hardcoded, so tests can run against a much smaller dataset:

| Property | Default (dev/prod) | Test override |
|---|---|---|
| `app.seed.user-count` | 100 | 10 |
| `app.seed.category-count` | 100 | 10 |
| `app.seed.product-count` | 10000 | 50 |
| `app.seed.min-reviews-per-product` | 3 | 1 |
| `app.seed.max-reviews-per-product` | 10 | 3 |
| `app.seed.order-count` | 3000 | 20 |

Seeding order respects FK dependencies: users → categories (parents before children, or a two-pass insert setting `parent_id` after all rows exist) → products → product_categories (each product gets 1–3 random categories) → reviews (3–10 per product, random author from the seeded users) → orders + order_items (random user, 1–5 line items of random products/quantities, `unit_price_cents` snapshot from the product's current price, random `status`, `stockQty` decremented accordingly so seeded data is internally consistent). Inserts use JDBC batching (`spring.jpa.properties.hibernate.jdbc.batch_size`) rather than one `save()` per row — at 10k+ products and tens of thousands of reviews, row-at-a-time inserts would make every fresh `docker compose up` + app start noticeably slow.

## GraphQL schema additions

```graphql
type Category {
  id: ID!
  name: String!
  parent: Category
  children(first: Int, after: String): CategoryConnection!
  products(filter: ProductFilter, first: Int, after: String): ProductConnection!
}
type CategoryConnection { edges: [CategoryEdge!]! pageInfo: PageInfo! totalCount: Int! }
type CategoryEdge { node: Category! cursor: String! }

type User {
  id: ID!
  username: String!
  displayName: String!
  role: Role!
  orders(first: Int, after: String): OrderConnection!
}
enum Role { CUSTOMER ADMIN }

type Order {
  id: ID!
  user: User!
  status: OrderStatus!
  placedAt: String!
  items: [OrderItem!]!
  totalCents: Int!
}
enum OrderStatus { PENDING PAID SHIPPED DELIVERED CANCELLED }
type OrderItem {
  id: ID!
  product: Product!
  quantity: Int!
  unitPriceCents: Int!
  lineTotalCents: Int!
}
type OrderConnection { edges: [OrderEdge!]! pageInfo: PageInfo! totalCount: Int! }
type OrderEdge { node: Order! cursor: String! }

input PlaceOrderInput { items: [OrderItemInput!]! }
input OrderItemInput { productId: ID!, quantity: Int! }

extend type Product {
  stockQty: Int!
  categories: [Category!]!
}

# Review.author changes type String! -> User! (same field name, new meaning)

extend type Query {
  categories(first: Int, after: String): CategoryConnection!
  category(id: ID!): Category
  me: User!
  order(id: ID!): Order                                          # owner or ADMIN only
  orders(status: OrderStatus, first: Int, after: String): OrderConnection!   # ADMIN only
}

extend type Mutation {
  placeOrder(input: PlaceOrderInput!): Order!         # user resolved from principal; validates & decrements stock
  updateOrderStatus(id: ID!, status: OrderStatus!): Order!   # ADMIN only
}
```

`AddReviewInput` drops its `author: String!` field — the author is now resolved from the authenticated principal (see Authorization below), not passed by the caller.

No order-related subscription is added. `reviewAdded` already demonstrates the subscription pattern; nothing about orders needs real-time push for this demo, and adding one would be pattern-coverage for its own sake rather than in service of a concrete gap (see Scope limits).

## Pagination strategy

The existing `CursorPagination` utility (Base64-encoded list-position cursor, in-memory `stream().skip().limit()`) loads its entire input list before slicing. That's fine at review-per-product scale (3–10) but wrong for a 10,000-row product table — loading the whole table on every `products` query defeats the purpose of having a database.

Two strategies coexist, chosen per listing based on how large its backing set can actually get:

- **DB-level keyset pagination (new):** top-level `products`, `Category.products`, top-level admin `orders`. Cursor encodes the last-seen row `id` (a stable, indexed, monotonic key); the query is `WHERE id > :cursorId ORDER BY id LIMIT :first` (plus whatever `ProductFilter`/`status` predicate applies), pushed down via a Spring Data repository method rather than loaded into a `List` first.
- **Existing in-memory `CursorPagination` (unchanged):** `Category.children` (a node has at most a handful of children out of only 100 categories total), `Product.reviews` (3–10 per product), `User.orders` (tens per user). These stay batch-loaded as a full raw list per key, then sliced in memory — exactly today's `reviews` pattern — because the backing set per parent is inherently small regardless of overall table size.

## Resolvers & DataLoaders

Several new relations take no arguments, so — unlike `Product.reviews`, which needs manual `BatchLoaderRegistry` registration specifically because its `@SchemaMapping` method needs `@Argument` for filter/pagination — they can use Spring GraphQL's `@BatchMapping` directly. This is deliberately demonstrated side by side with the existing manual-registration pattern:

| Field | Batch key | Mechanism |
|---|---|---|
| `Product.categories` | productId → `List<Category>` (via join table) | `@BatchMapping` |
| `Category.parent` | parentId → `Category` | `@BatchMapping` (shared `categoriesById` loader) |
| `Review.author` | authorId → `User` | `@BatchMapping` (shared `usersById` loader) |
| `Order.user` | userId → `User` | `@BatchMapping` (same `usersById` loader) |
| `OrderItem.product` | productId → `Product` | `@BatchMapping` (shared `productsById` loader) |
| `Order.items` | orderId → `List<OrderItem>` (plain list, not paginated) | `@BatchMapping` |
| `Category.children` | parentId → `List<Category>`, then in-memory `CursorPagination` | manual `DataLoader`, same idiom as `reviews` (only ~100 categories total, so a node's full child list is cheap to load) |
| `User.orders` | userId → `List<Order>`, then in-memory `CursorPagination` | manual `DataLoader`, same idiom (tens of orders per user) |

**`Category.products` gets no DataLoader.** Batching it the way `reviews` works today would mean loading a category's *entire* unpaginated product list (potentially thousands of rows) per key just to slice it in memory afterward — exactly the anti-pattern the pagination-strategy split above exists to avoid. Instead it's a direct `@SchemaMapping` issuing one small keyset query per category node actually being resolved, bounded by the outer `categories(first: ...)` argument (at most ~50 small queries, not one huge one). This is a deliberate, documented trade-off: paginated nested connections are a known limitation of DataLoader-style batching, and this module calls it out explicitly rather than papering over it.

## Authorization

- `me`, `placeOrder`, `addReview` — `@PreAuthorize("isAuthenticated()")`; current `User` resolved by looking up the Basic-Auth principal's username via `usersById`/a dedicated by-username lookup.
- `orders` (admin browse-all), `updateOrderStatus` — `@PreAuthorize("hasRole('ADMIN')")`, same as today's `deleteReview`.
- `order(id)` — **new pattern**: ownership can't be expressed as a static `@PreAuthorize` expression since it depends on the loaded row. The resolver loads the order, then throws `AccessDeniedException` unless the caller is either the owning user or `ADMIN`. This is genuinely new coverage: row-level/ownership authorization, distinct from every existing check in this module, which is role-only. It reuses `DemoExceptionResolver`'s existing `AccessDeniedException` → `FORBIDDEN`/`UNAUTHORIZED` classification from the security-roles addendum — no new error-handling code needed.

## `placeOrder` transactional logic

Single `@Transactional` service method:

1. Resolve current `User` from the authenticated principal.
2. For each `OrderItemInput`: fetch the `Product` row with a pessimistic write lock (`@Lock(LockModeType.PESSIMISTIC_WRITE)` on the repository method) so concurrent `placeOrder` calls against the same product can't oversell. Pessimistic locking is chosen over optimistic-lock-with-retry for simplicity in a demo context — correct on single-node Postgres, no retry loop needed.
3. If `stockQty < quantity`, throw a business exception (`InsufficientStockException` or similar) — surfaced as a GraphQL partial error via `DemoExceptionResolver`, the same partial-failure story `FailureSimulator`-driven `product(id)` already tells.
4. Otherwise decrement `stockQty` and snapshot `unitPriceCents = product.priceCents` into a new `OrderItem`.
5. After all lines succeed, create the `Order` with `status = PENDING`, `placedAt = now()`, and `totalCents = sum(quantity * unitPriceCents)`.

If any line fails validation, the whole transaction rolls back — no partial order, no partially-decremented stock.

## Testing

- New repository-level tests for the keyset pagination queries (`products`, `Category.products`, admin `orders`): correctness at page boundaries, `hasNextPage`, stable ordering under concurrent inserts.
- `DemoDataSeeder` test: with the small test-profile volumes, asserts row counts land in the configured ranges (e.g. every product has between `min` and `max` reviews) and that re-running the seeder against a non-empty DB is a no-op.
- `DemoControllerTest`/`DemoIntegrationTest` extended for `categories`, `me`, `order`, `orders`, `placeOrder` (including the insufficient-stock failure path and the all-or-nothing rollback), `updateOrderStatus`, and the new authorization cases (row-level ownership on `order(id)`, admin-only on `orders`/`updateOrderStatus`).
- Existing `CursorPaginationTest` and `FailureSimulatorTest` are unaffected — the in-memory utility itself doesn't change, only which listings use it.
- Gatling (`DemoSimulation.java`) and JMeter (`DemoSimulation.jmx`) load-test coverage for the new operations is worth doing but is treated as implementation-plan-level detail, not part of this schema design.

## Docs

`communication-protocols/graphql/README.md` and `spring-demo/README.md` get a new domain-model section: an ER-style summary of the six entities and their relations, the keyset-vs-in-memory pagination split and why, the `@BatchMapping`-vs-manual-`DataLoader` contrast table above, the row-level authorization example (`order(id)` as owner vs. as a different user vs. as admin), and `docker compose -f communication-protocols/graphql/docker/docker-compose.yml up -d` added to the module's run instructions.

## Scope limits

- No order-related subscription (see GraphQL schema additions section) — not needed to demonstrate a new pattern, `reviewAdded` already covers subscriptions.
- No payment integration, no cancellation/refund flow, no shipment tracking — `OrderStatus` is a plain enum settable only via `updateOrderStatus`, with no state-machine validation of legal transitions (e.g. nothing stops `DELIVERED` → `PENDING`). A real state machine is out of scope; flagged as a natural follow-up, not implemented here.
- Pessimistic locking is single-node-Postgres-appropriate; no distributed-lock or multi-region consideration (consistent with this repo's "local demo" scope everywhere else).
- Gatling/JMeter load-test updates for the new schema are out of scope for this design; to be scoped during implementation planning.
- `Category.products`' one-query-per-node approach is not batched — an accepted, documented trade-off (see Resolvers & DataLoaders), not a bug to fix later.
- Seed data volumes (100/100/10000/3–10/3000) are fixed defaults in `application.yml`, overridable but not dynamically configurable at runtime — consistent with `FAILURE_RATE`-style fixed constants elsewhere in this repo.
