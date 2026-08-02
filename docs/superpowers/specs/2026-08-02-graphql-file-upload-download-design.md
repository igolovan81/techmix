# GraphQL File Upload/Download (Sixth Pattern) Design

**Date:** 2026-08-02
**Status:** Approved

## Overview

Adds a sixth pattern to the GraphQL demo (`communication-protocols/graphql/`): binary file transfer for a `Product`'s image. GraphQL has no native support for binary payloads — queries/mutations are JSON — so the schema only ever exposes a pointer field, `Product.imageUrl: String`, while the actual bytes move over two plain REST endpoints living alongside `/graphql`:

- `POST /api/products/{id}/image` (multipart, ADMIN-only) — upload/replace
- `GET /api/products/{id}/image` (public) — download/stream

This is deliberately the "REST sidecar" pattern used by production GraphQL APIs (GitHub, Shopify, etc.), not the `graphql-multipart-request-spec` extension — it keeps large binary payloads off the GraphQL execution engine and plays well with caching/CDNs. It also lets the demo show, concretely, a case where GraphQL is the wrong tool for part of a domain.

## Goals

- One optional image per `Product`, uploadable/replaceable by an ADMIN, downloadable by anyone.
- `Product.imageUrl` resolved without introducing an N+1 (reuses the DataLoader/`@BatchMapping` lesson from pattern #2, applied to a new field).
- Angular `product-detail` page shows the image if present and lets an ADMIN upload/replace it, matching the existing admin-gated-control pattern already used for review deletion.
- Document this as the sixth pattern in the GraphQL module's README, with the same Pros/Cons/Typical-use-cases treatment as the other five.

## Non-goals

- No multi-image galleries — one image per product, upload replaces the previous one.
- No thumbnails on `product-list` — image display is scoped to `product-detail` only.
- No `graphql-multipart-request-spec` / Apollo Upload — file transfer intentionally bypasses GraphQL entirely.
- No image processing (resizing, format conversion, EXIF stripping) — stored and served as uploaded, content-type validated only at the "starts with `image/`" level.
- No CDN/production hardening — same scope limit as the rest of this module ("no TLS", "no production hardening").

## Backend (`graphql/spring-demo`)

### Data model

New table `product_images` (one row max per product):

| Column | Type | Notes |
|---|---|---|
| `product_id` | bigint | PK, FK → `products.id` |
| `content_type` | varchar | e.g. `image/png` |
| `data` | bytea | raw image bytes |
| `updated_at` | timestamp | set on insert/replace |

New Liquibase changelog `db.changelog-8-create-product-images-table.xml`, registered in `db.changelog-master.xml`. Stored in the existing Postgres instance — no new infrastructure, consistent with the rest of this module's "just Postgres" footprint.

New `ProductImageEntity` (mirrors the existing entity conventions in `entity/`) and `ProductImageRepository` (`existsByProductId`, `findByProductId`, `save`, `deleteByProductId`).

### Schema change

```graphql
type Product {
    id: ID!
    name: String!
    priceCents: Int!
    stockQty: Int!
    categories: [Category!]!
    reviews(filter: ReviewFilter, first: Int, after: String): ReviewConnection!
    imageUrl: String
}
```

`imageUrl` is `null` when no image has been uploaded for that product.

### GraphQL resolution

`DemoController` gains a `@BatchMapping(typeName = "Product", field = "imageUrl")` method — no `@Argument` is needed, so this follows the same direct-`@BatchMapping` idiom as `productCategories`/`orderItemProduct`, not a manually registered `DataLoader`. It runs one batched existence check (`ProductImageRepository` query scoped to the requested product ids) and returns `"/api/products/" + id + "/image"` for ids that have a row, `null` otherwise. This avoids resolving the field with one query per product regardless of how many products a client's query touches — the same N+1 lesson pattern #2 (DataLoader batching) already teaches, now applied to this field.

### REST endpoints

New `ProductImageController` (`@RestController`, sits beside `DemoController`):

- **`POST /api/products/{id}/image`** — `@PreAuthorize("hasRole('ADMIN')")`, `@RequestParam("file") MultipartFile`. Validates:
  - product `{id}` exists → `404` if not
  - `file.getContentType()` starts with `image/` → `400` if not
  - Upserts the `product_images` row (replaces any existing one), sets `updated_at`.
  - Returns `204 No Content`.
- **`GET /api/products/{id}/image`** — no method-level auth annotation; allowed at the HTTP layer (see Security below). Streams `data` with the stored `Content-Type` header. `404` if no row exists for that product id.

**Size limits:** `application.yml` adds:
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 5MB
```
A local `@ExceptionHandler(MaxUploadSizeExceededException.class)` on `ProductImageController` maps that to `413`. Bad-content-type and missing-product cases are handled by a plain `IllegalArgumentException`-based path mapped to `400`/`404` within the same controller — kept local rather than extending `DemoExceptionResolver`, which is GraphQL-specific (`DataFetcherExceptionResolver`) and has no bearing on this plain REST controller.

No `FailureSimulator` usage here — that convention (5% simulated failure rate) is scoped to the `message-brokers/` modules and isn't otherwise used anywhere in this module's REST surface (there is none, today).

### Security

`SecurityConfig` gains one matcher, added before the existing catch-all:

```java
.requestMatchers(HttpMethod.GET, "/api/products/*/image").permitAll()
```

`POST /api/products/{id}/image` falls through to the existing `anyRequest().authenticated()`, with the ADMIN role check enforced by `@PreAuthorize` on the controller method — the same split `/graphql` already uses (HTTP-layer `permitAll` + method-level `@PreAuthorize` for per-operation rules).

## Frontend (`graphql/angular-demo`)

### Plumbing

- `proxy.conf.json` gains a second entry proxying `/api` to `http://localhost:8092` (same shape as the existing `/graphql` entry, `ws` not needed).
- `graphql.models.ts`: `Product` interface gains `imageUrl?: string | null`.
- `catalog.gql.ts`: `PRODUCT_QUERY` requests `imageUrl` (the list query, `PRODUCTS_QUERY`, is unchanged — no thumbnails in scope).
- `product-catalog.service.ts` gains `uploadProductImage(id: string, file: File): Observable<void>`, issuing a plain `HttpClient.post` with a `FormData` body — a REST call, not routed through Apollo. The existing `authInterceptor` attaches the `Authorization: Basic ...` header to every `HttpClient` request regardless of target path, so no additional auth wiring is needed for this call.

### UI

`product-detail.html`/`.ts`:
- If `product().imageUrl` is set, render `<img [src]="...">` in the product header; otherwise a placeholder block.
- An ADMIN-only file input + "Upload image" control, gated the same way the existing delete-review button is gated (`authService.currentUser()?.role === 'ADMIN'`).
- After a successful upload, the component appends a cache-busting query parameter (`?v=Date.now()`) to the rendered image URL, since the underlying REST path is unchanged by a replace and the `<img>` tag would otherwise keep showing a cached/stale image.

## Documentation

- `communication-protocols/graphql/README.md` — "five patterns" table becomes six, with a new **File upload/download** row (`Product.imageUrl` + REST sidecar). New Pros/Cons/Typical-use-cases subsection matching the format of the other five. The intro paragraph and `## Scope` section each get a one-line note that file transfer is intentionally REST, not GraphQL multipart, and why.
- `communication-protocols/README.md` — the GraphQL row's "Demo" cell gains "...plus REST-sidecar image upload/download".
- `spring-demo/README.md` — new curl walkthrough (`curl -u admin:adminPassword -F file=@image.jpg http://localhost:8092/api/products/1/image`, then a plain `curl` download), added next to the existing Product section of the domain-model write-up.
- `angular-demo/README.md` — one line noting the product-detail page shows/uploads an image via a plain REST call, not Apollo.

## Testing strategy

**Backend:**
- New `ProductImageControllerTest` (`@SpringBootTest` + `MockMvc`, following `DemoIntegrationTest`'s setup conventions): ADMIN upload → `204`, then `GET` returns the uploaded bytes/content-type; USER and anonymous upload attempts → `403`/`401`; `GET` for a product with no image → `404`; non-image content-type upload → `400`; upload to a non-existent product id → `404`; oversized upload → `413`.
- `DemoIntegrationTest` (GraphQlTester-based) gets one added case exercising the new `@BatchMapping`: a product with no image row resolves `imageUrl: null`; one with a row resolves the expected `/api/products/{id}/image` string.

**Frontend:**
- `product-catalog.service.spec.ts` gets a case for `uploadProductImage`.
- `product-detail.spec.ts` gets cases for image rendering (present/absent) and the admin-gated upload control, following the file's existing spec patterns (e.g. the add-review-dialog tests).
