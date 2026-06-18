# MongoDB Indexing & Text Search Pattern Design

**Date:** 2026-06-18
**Status:** Approved

## Overview

Add a fifth MongoDB pattern — indexing and text search — to the existing demo at `noSQL/mongodb/spring-demo`. Reuses the `products` collection (no new collection, no changes to the `Product` entity), matching the module's established "one pattern = one package = one REST surface = one README section" convention already used by CRUD, transactions, change streams, and aggregation.

## Why this pattern, and why now

This was identified as the one capability missing from the existing four patterns: all of them are about *writing and reacting to* data (create/update, atomic multi-document writes, real-time change notification, rolled-up analytics) — none demonstrate *querying efficiently at scale*, which is one of MongoDB's core value propositions. It was explicitly scoped out of the original demo to keep that build tight; this spec adds it back in as a standalone increment.

## Indexes

Two indexes, created explicitly at application startup (not via `@Indexed`/`@TextIndexed` annotations plus `auto-index-creation`, which would be implicit and harder to point to in a walkthrough) — this mirrors how `OrderChangeStreamListener` already registers its change-stream subscription explicitly in `@PostConstruct`, the established style for "infrastructure the app sets up for itself on boot" in this module:

1. **Text index on `Product.name`** — backs `$text` search with MongoDB's built-in relevance scoring and stemming.
2. **Compound index on `(price, stock)`** — backs efficient range queries that filter on both fields, the classic case where a single-field index can't help but a compound index can.

## New package: `com.testingai.mongodb.search`

- **`SearchIndexInitializer`** — a `@Component` whose `@PostConstruct` method creates both indexes via `MongoTemplate.indexOps(Product.class)`. Idempotent — `ensureIndex` is a no-op if the index already exists, so this is safe to run on every startup against the same replica set.
- **`ProductSearchService`** — two methods:
  - `searchByText(String query): List<Product>` — runs a `$text` search against the text index.
  - `findByPriceRange(double min, double max): List<Product>` — runs a range query against the compound index.

## REST API additions

Added to the existing `DemoController`:

```
GET /demo/products/search?q=<text>           full-text search over product names
GET /demo/products/price-range?min=&max=     range query backed by the compound index
```

Both coexist safely with the existing `GET /demo/products/{id}` — Spring MVC resolves the literal paths (`/search`, `/price-range`) ahead of the `{id}` path-variable pattern by specificity, a standard and well-understood routing behavior, so no path-mapping ambiguity.

## Showing the indexes are actually used

Rather than threading MongoDB's native `explain()` output through the application layer — which would require the application code (and its tests) to drop down to the native `MongoCollection<Document>` driver and mock a multi-step `find().explain()` chain, breaking from the module's established "mock `MongoTemplate` directly" testing convention — this is handled the same way the existing "Replica set admin commands" README section already handles replica-set inspection: documented `mongosh` commands the reader runs themselves.

New README section, "Verify the indexes are used":

```bash
# Confirm the indexes exist
docker exec mongo1 mongosh ecommerce --quiet --eval "db.products.getIndexes()"

# Text search uses the text index (look for IXSCAN on the text index in the plan)
docker exec mongo1 mongosh ecommerce --quiet --eval 'db.products.find({ $text: { $search: "Widget" } }).explain("executionStats")'

# Price-range query uses the compound index (look for IXSCAN on price_1_stock_1)
docker exec mongo1 mongosh ecommerce --quiet --eval 'db.products.find({ price: { $gte: 5, $lte: 50 } }).sort({ stock: -1 }).explain("executionStats")'
```

## Testing

Unit tests for `ProductSearchService`, mocking `MongoTemplate` directly — identical style to `ProductServiceTest`/`OrderServiceTest`/`OrderAggregationServiceTest`. `SearchIndexInitializer` gets a lightweight test confirming it calls `indexOps(Product.class).ensureIndex(...)` twice (once per index) without throwing, mirroring `OrderChangeStreamListenerTest`'s "doesn't throw" style for startup-registration components.

`DemoControllerTest` gets two new test methods for the new endpoints, following the existing `@WebMvcTest` + `@MockitoBean` pattern.

## Documentation

- `noSQL/mongodb/README.md` gets: two new curl examples (search, price-range) in "Trigger endpoints"; a new row in "Collection characteristics" noting the two indexes on `products`; the new "Verify the indexes are used" section described above.
- The architecture Mermaid diagram's "CRUD Pattern" subgraph gains the two new query paths (`ProductSearchService` reading from `products` via the text/compound indexes).

## Scope limits

- No new collection, no changes to the `Product` entity (no new fields needed — text search over `name` alone is sufficient to demonstrate the capability).
- No `explain()` output surfaced through the application/REST layer — verified via documented `mongosh` commands instead, consistent with how replica-set internals are already inspected in this module.
- No sharding, no geospatial indexes, no TTL indexes — out of scope for this increment (each would be its own pattern if added later).
