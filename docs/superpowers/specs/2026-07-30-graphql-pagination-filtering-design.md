# GraphQL Demo — Pagination & Filtering Addendum Design

**Date:** 2026-07-30
**Status:** Approved
**Builds on:** `docs/superpowers/specs/2026-07-29-graphql-communication-protocol-demo-design.md` (original GraphQL demo — query/DataLoader/mutation/subscription patterns), `docs/superpowers/specs/2026-07-30-graphql-security-roles-design.md` (security/roles addendum)

## Overview

Adds a 6th pattern to `communication-protocols/graphql/spring-demo`: cursor-based pagination and input-object filtering. Today `products: [Product!]!` returns the entire 40-item catalog and `Product.reviews: [Review!]!` returns every review for a product — neither is filterable or paginated. This addendum replaces both with Relay-style connections (`edges`/`node`/`cursor`/`pageInfo`), the GraphQL-idiomatic pagination shape, since this module exists to teach idiomatic patterns rather than the shortest path to a working demo.

Both `products` and `Product.reviews` get pagination and filtering — this is the most complete demonstration of the pattern, showing it at both the root-query level and the nested/batch-mapped level.

## Schema change

```graphql
type PageInfo {
    hasNextPage: Boolean!
    endCursor: String
}

type ProductConnection {
    edges: [ProductEdge!]!
    pageInfo: PageInfo!
    totalCount: Int!
}
type ProductEdge {
    node: Product!
    cursor: String!
}

type ReviewConnection {
    edges: [ReviewEdge!]!
    pageInfo: PageInfo!
    totalCount: Int!
}
type ReviewEdge {
    node: Review!
    cursor: String!
}

input ProductFilter {
    nameContains: String
    minPriceCents: Int
    maxPriceCents: Int
}
input ReviewFilter {
    minRating: Int
}

type Query {
    products(filter: ProductFilter, first: Int, after: String): ProductConnection!
    product(id: ID!): Product
}

type Product {
    id: ID!
    name: String!
    priceCents: Int!
    reviews(filter: ReviewFilter, first: Int, after: String): ReviewConnection!
}
```

This is a breaking schema change: `products` moves from a bare array to a connection, and `Product.reviews` moves from `[Review!]!` to `ReviewConnection!`. Both `ProductFilter` fields and the single `ReviewFilter` field are optional/nullable; omitting a filter field means "no constraint on that criterion," and omitting the whole `filter` argument means "no filtering at all." Multiple `ProductFilter` fields are ANDed together.

Pagination is **forward-only** (`first`/`after`; no `last`/`before`) — full bidirectional Relay connections add real complexity for a demo whose purpose is to teach the pagination *pattern*, not exhaust the connection spec. `first` defaults to 10 if omitted and is clamped to a max of 50, so a caller can't force an unbounded response.

## Implementation

New package `com.testingai.graphql.pagination`:

```java
public record PageInfo(boolean hasNextPage, String endCursor) {}
public record Edge<T>(T node, String cursor) {}
public record Connection<T>(List<Edge<T>> edges, PageInfo pageInfo, int totalCount) {}

public final class CursorPagination {
    private static final int DEFAULT_FIRST = 10;
    private static final int MAX_FIRST = 50;

    private CursorPagination() {}

    public static <T> Connection<T> paginate(List<T> items, Integer first, String after) { ... }
    // cursor = Base64("cursor:<index>"); decode throws IllegalArgumentException on malformed input
}
```

One generic `Connection<T>`/`Edge<T>` implementation backs both the GraphQL `ProductConnection` and `ReviewConnection` types — Spring GraphQL resolves fields (`edges`, `node`, `cursor`, `pageInfo`, `totalCount`) by property access via reflection, so the runtime Java class doesn't need to match the GraphQL type name. This avoids duplicating identical connection-wrapping code per entity type.

**Ordering before pagination:** products are filtered, then kept in existing catalog order (stable `p1..p40` id order); reviews are filtered, then kept in insertion order (current behavior, unchanged). A cursor is a position in that filtered, ordered list, so — standard Relay caveat — a cursor issued under one filter is not valid against a different filter. This is called out in the README rather than guarded against in code (out of scope; see Scope limits).

**Service changes:**
- `ProductCatalogService`: new `listProducts(ProductFilter filter)` applying `nameContains` (case-insensitive substring), `minPriceCents`, `maxPriceCents` — all optional, ANDed. Existing no-arg `listProducts()` is kept for internal callers (e.g. `findProduct`, review seeding).
- `ReviewService.findByProductIds`: gains a `ReviewFilter filter` parameter, applying `minRating` to each product's list before the caller paginates.
- `DemoController`:
  - `products(ProductFilter filter, Integer first, String after)` replaces the no-arg `products()`, returning `Connection<Product>` (filter → `CursorPagination.paginate`).
  - The `@BatchMapping reviews(...)` method gains `@Argument ReviewFilter filter, @Argument Integer first, @Argument String after` and returns `Map<Product, Connection<Review>>`. Since all products being resolved in one query share the same field selection, they share the same filter/pagination arguments — this is compatible with `@BatchMapping`'s one-call-per-query-per-field contract; no per-product argument variance is needed or supported.

## Testing

- **New `CursorPaginationTest`** (in the new `pagination` package): empty list; single full page; exact page-size boundary; `after` pointing past the last item (empty result, `hasNextPage=false`); malformed cursor (throws, not silently mishandled); `first` omitted (defaults to 10); `first` over the 50 cap (clamped); `first <= 0` (rejected).
- **`ProductCatalogServiceTest`**: each `ProductFilter` field in isolation, fields combined, and the no-filter passthrough still returning the full 40-item catalog.
- **`ReviewServiceTest`**: `minRating` filtering, combined with the existing `batchCallCount` assertion (filtering must not change the batch-call-count contract).
- **`DemoControllerTest` / `DemoIntegrationTest`**: existing queries rewritten to the connection shape (`products(first: 40) { edges { node { id name } } totalCount }`, `reviews(first: 10) { edges { node { id author rating } } } }`); new cases paging through filtered products across 2+ pages and asserting `hasNextPage`/`endCursor` progression; confirms authorization is unaffected — pagination/filtering isn't gated, same public/authenticated split as today.
- **Gatling `DemoSimulation.java`** and **JMeter `DemoSimulation.jmx`**: existing scenarios querying `products`/`reviews` updated to the connection shape so the load tests keep working against the new schema.

## Docs

`communication-protocols/graphql/README.md` and `spring-demo/README.md` get a new pagination/filtering section: the schema snippet above, one example query combining `filter` + `first`/`after`, and the "a cursor is only valid for the filter it was issued under" caveat — matching the style of the existing security/roles section.

## Scope limits

- Forward-only pagination (`first`/`after`) — no `last`/`before`, no full bidirectional Relay connection support.
- Cursors encode a plain list position (Base64 of `"cursor:<index>"`), not an opaque database key — fine for this demo's in-memory, single-process data, not representative of a cursor scheme that must survive underlying data reordering.
- No protection against a cursor issued under one filter being replayed against a different filter (or against the unfiltered list) — the caller gets whatever position that decodes to, which may be a different logical page than intended. Documented in the README as a known caveat rather than guarded against.
- `first` cap (50) and default (10) are fixed constants, not configurable per-deployment — consistent with this repo's "local demo" scope elsewhere (e.g. `FAILURE_RATE` in `FailureSimulator`).
