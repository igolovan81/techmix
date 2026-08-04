# GraphQL Angular Demo Design

**Date:** 2026-08-01
**Status:** Approved

## Overview

A new standalone Angular application at `communication-protocols/graphql/angular-demo/`, sibling to `graphql/spring-demo`. It is a browser client exercising all five GraphQL patterns the server demonstrates (query + nested fetch, DataLoader batching, mutation, subscription, pagination & filtering) against the existing e-commerce domain (Product, Category, Review, User, Order, OrderItem).

**Correction (post-implementation):** this doc originally claimed zero changes to `graphql/spring-demo`. That assumption was wrong and was caught by testing the WebSocket flow directly during implementation, not by inspection: `httpBasic()`'s `BasicAuthenticationFilter` defaults to its own `RequestAttributeSecurityContextRepository`, which never touches the `HttpSession` — so `SecurityConfig` as written never actually issued a `JSESSIONID`, and the subscription had no way to authenticate. The fix is a one-line addition to `SecurityConfig` (`httpBasic(basic -> basic.securityContextRepository(new HttpSessionSecurityContextRepository()))`), verified end-to-end afterward (HTTP login → `Set-Cookie` → WS handshake using only that cookie → subscription opens and receives events). See the corresponding note in `SecurityConfig.java`.

## Goals

- Give every one of the five patterns documented in `communication-protocols/graphql/README.md` a working UI: browse/filter/paginate products and categories, read nested reviews, add/delete a review, place an order and view order history, and watch a live `reviewAdded` subscription feed.
- Demonstrate the role-based and row-level authorization differences between the seeded `user`/`admin` accounts (e.g. `orders(status, first, after)` and `deleteReview`/`updateOrderStatus` are ADMIN-only; `order(id)` is owner-or-admin).
- Connect to the running Spring app with zero changes to `graphql/spring-demo`.

## Non-goals

- No GraphQL code generation — TS types mirroring `schema.graphqls` are hand-written (`core/graphql/graphql.models.ts`); no build-time codegen step.
- No e2e test layer — unit tests only (Karma/Jasmine), matching this module's Maven siblings' "unit tests, no e2e" convention.
- No production reverse-proxy / deployment story — the dev-server proxy described below is dev-only, same scope limit as the rest of the `communication-protocols` demos ("no TLS", "no production hardening").
- No SSR.

## Connecting to the Spring app

Two constraints, both solved without touching `graphql/spring-demo`:

**CORS.** The Spring app has no CORS configuration, so cross-origin requests from `:4202` would be blocked by the browser. `ng serve` uses `proxy.conf.json` to proxy `/graphql` (HTTP and WebSocket) to `http://localhost:8092`:

```json
{
  "/graphql": {
    "target": "http://localhost:8092",
    "secure": false,
    "ws": true,
    "changeOrigin": true
  }
}
```

The browser sees everything as same-origin `:4202`; no backend CORS bean needed.

**WebSocket authentication.** Browsers cannot attach an `Authorization` header to a native WebSocket handshake, so HTTP Basic auth (used for every query/mutation) can't authenticate the `reviewAdded` subscription directly. `SecurityConfig` never sets `SessionCreationPolicy.STATELESS`, so the first Basic-authenticated HTTP request causes Spring Security to persist the `Authentication` to an `HttpSession` and set a `JSESSIONID` cookie. Login therefore runs one HTTP-Basic-authenticated request — the `me` query, which also fetches the user's role/displayName — before navigating into the app. Because the WebSocket connects through the same proxied origin, the browser automatically attaches that session cookie to the WS handshake, and Spring Security authenticates the subscription from the session. This is a real consequence of the existing `SecurityConfig`, not a workaround, and requires no backend change.

Consequence: the whole app sits behind `/login`, even though `products`/`categories`/`category` queries don't themselves require auth — login is also what stands up the session subscriptions need.

## Repository structure

```
communication-protocols/graphql/
├── spring-demo/                          (existing, untouched)
└── angular-demo/
    ├── angular.json
    ├── package.json                      (Angular ^21.0.0, @angular/material, apollo-angular, @apollo/client, graphql, graphql-ws; "start" script uses --port 4202 --proxy-config proxy.conf.json)
    ├── proxy.conf.json
    ├── README.md
    └── src/
        ├── main.ts
        ├── styles.scss                   (Angular Material theme setup)
        └── app/
            ├── app.ts                    (shell: mat-toolbar nav + router-outlet; /login renders outside the shell)
            ├── app.routes.ts
            ├── app.config.ts             (provideRouter, provideHttpClient(withInterceptors([authInterceptor])), provideAnimationsAsync, provideApollo)
            ├── core/
            │   ├── auth/
            │   │   ├── auth.service.ts           (signal-based current user + credentials; sessionStorage-backed)
            │   │   ├── auth.service.spec.ts
            │   │   ├── auth.interceptor.ts        (adds `Authorization: Basic ...` to every HTTP request)
            │   │   ├── auth.interceptor.spec.ts
            │   │   ├── auth.guard.ts              (CanActivateFn -> redirect to /login if unauthenticated)
            │   │   └── auth.models.ts             (AuthUser, Role)
            │   └── graphql/
            │       ├── apollo.provider.ts         (HttpLink + GraphQLWsLink split, error link)
            │       ├── apollo.provider.spec.ts    (error-link classification behavior)
            │       └── graphql.models.ts           (Product, Category, Review, User, Order, OrderItem, connections, filters, inputs, enums — hand-written)
            ├── shared/
            │   └── connection-paginator/
            │       ├── connection-paginator.ts     ("Load more" + totalCount, reused by catalog/categories/orders)
            │       └── connection-paginator.spec.ts
            └── features/
                ├── login/
                │   ├── login.ts                    (quick-select user/admin + manual form; runs `me` on submit)
                │   ├── login.html
                │   └── login.spec.ts
                ├── catalog/
                │   ├── product-list.ts / .html / .spec.ts
                │   ├── product-detail.ts / .html / .spec.ts  (categories, nested reviews, add/delete review)
                │   ├── add-review-dialog.ts / .html
                │   ├── catalog.gql.ts
                │   └── catalog.routes.ts
                ├── categories/
                │   ├── category-tree.ts / .html / .spec.ts   (mat-tree; children + products per node)
                │   ├── categories.gql.ts
                │   └── categories.routes.ts
                ├── live-reviews/
                │   ├── live-reviews.ts / .html / .spec.ts    (reviewAdded subscription, product filter dropdown)
                │   └── live-reviews.gql.ts
                └── orders/
                    ├── order-list.ts / .html / .spec.ts       (My Orders; All Orders tab for ADMIN)
                    ├── order-detail.ts / .html               (items, status; ADMIN updateOrderStatus)
                    ├── place-order.ts / .html                (cart review + placeOrder)
                    ├── cart.service.ts / .spec.ts             (signal-based, add from catalog)
                    ├── orders.gql.ts
                    └── orders.routes.ts
```

## Feature tour (routes)

| Route | Pattern(s) demonstrated | Auth |
|---|---|---|
| `/login` | HTTP Basic auth, session establishment for later WS auth | — |
| `/catalog` | `products(filter, first, after)` — pagination & filtering | none required server-side (gated by app-wide login anyway) |
| `/catalog/:id` | nested `reviews(filter, first, after)` (query + nested fetch, DataLoader batching), `addReview` mutation, `deleteReview` (ADMIN only) | `addReview`/`deleteReview` need auth |
| `/categories` | `categories` → `children(first, after)` (batched via `categoryChildren` loader), `category.products(filter, first, after)` | none required server-side |
| `/live` | `reviewAdded(productId?)` subscription | isAuthenticated |
| `/orders` | `me.orders(first, after)` (customer), `orders(status, first, after)` (ADMIN, role-only), `order(id)` (owner-or-admin, row-level) | isAuthenticated; ADMIN tab for all-orders |
| `/orders` (place order) | `placeOrder` mutation | isAuthenticated |
| `/orders/:id` (status change) | `updateOrderStatus` mutation | ADMIN only |

## Error handling

An Apollo `ErrorLink` centralizes handling instead of per-component try/catch:

- `extensions.classification === 'UNAUTHORIZED'` → clear the session, redirect to `/login`.
- `'FORBIDDEN'` → snackbar ("not allowed for this role"), stay on the page.
- `'BAD_REQUEST'` / `'INTERNAL_ERROR'` (including the simulated 5% `FailureSimulator` failures on `product(id)`) → snackbar with a retry affordance.

## Testing strategy

Karma/Jasmine unit tests, no e2e layer:

- `AuthService`, `auth.interceptor` — credential storage, header injection, guard redirect behavior.
- `apollo.provider` — error-link classification → snackbar/logout branching, tested against constructed `ErrorResponse` fixtures (no live network).
- `CartService` — add/remove/quantity logic.
- `connection-paginator` — "Load more" appends edges and respects `hasNextPage`.
- Component-level tests for filter forms and pagination triggering the right query variables (Apollo client mocked/stubbed, not hitting a real server).

## Commands (added to CLAUDE.md)

```bash
cd communication-protocols/graphql/angular-demo

npm install
npm start              # dev server on :4202, proxies /graphql (HTTP + WS) to :8092
npm test                # Jasmine/Karma unit tests
npm run build           # production build
```

Requires `graphql/spring-demo` running first (`docker compose -f ../docker/docker-compose.yml up -d` then `mvn -pl graphql/spring-demo spring-boot:run`, per the existing README).
