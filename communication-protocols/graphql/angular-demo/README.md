# GraphQL Angular Demo

A standalone Angular 20 app that exercises every GraphQL pattern demonstrated by [`../spring-demo`](../spring-demo): query + nested fetch, DataLoader batching, mutation, subscription, pagination & filtering, and file upload/download — plus the role-based and row-level authorization built into that app's schema.

## Running

Requires `graphql/spring-demo` running first (see [`../README.md`](../README.md)):

```bash
docker compose -f ../docker/docker-compose.yml up -d
cd ../spring-demo && mvn spring-boot:run
```

Then, in this directory:

```bash
npm install
npm start   # dev server on :4202, proxies /graphql (HTTP + WS) to :8092
```

Open http://localhost:4202 and log in with the "Continue as user" or "Continue as admin" quick-select button (credentials match `graphql/spring-demo`'s seeded accounts).

## Why a dev proxy

The Spring app has no CORS configuration. `proxy.conf.json` makes `ng serve` proxy `/graphql` (both HTTP and the WebSocket upgrade) and `/api` (the REST image upload/download endpoints) to `http://localhost:8092`, so the browser sees everything as same-origin — no backend changes needed for this part. This is dev-only; there's no production deployment story here (same scope limit as the rest of `communication-protocols`).

## Why login establishes a session, not just a header

Every query/mutation carries an `Authorization: Basic` header (added by an `HttpInterceptor`). Browsers can't attach that header to a WebSocket handshake, though, so the `reviewAdded` subscription can't authenticate that way. Instead, login's one HTTP-Basic-authenticated request (`me`) causes Spring Security to persist the authentication to an `HttpSession` and set a `JSESSIONID` cookie. Because the WebSocket connects through the same proxied origin, the browser attaches that cookie to the WS handshake automatically, authenticating the subscription.

Unlike the dev proxy, this part **did** require a small `graphql/spring-demo` change: `httpBasic()`'s `BasicAuthenticationFilter` defaults to its own `RequestAttributeSecurityContextRepository`, which never touches the `HttpSession` — so no session was ever actually created until `SecurityConfig` was updated to use `HttpSessionSecurityContextRepository` explicitly. See the comment on `SecurityConfig` for the full explanation; this was discovered by testing the WebSocket flow directly, not by inspection.

This is also why the whole app sits behind `/login`, even though `products`/`categories` don't themselves require authentication — login is what stands up the session subscriptions need.

## Feature tour

| Route | Pattern(s) |
|---|---|
| `/login` | HTTP Basic auth, session establishment |
| `/catalog` | `products(filter, first, after)` |
| `/catalog/:id` | nested `reviews`, `addReview`, `deleteReview` (ADMIN), `imageUrl` display and (ADMIN) upload via a plain REST call — not Apollo, since GraphQL carries no binary payloads |
| `/categories` | `categories` → `children` (DataLoader-batched), `category.products` |
| `/live` | `reviewAdded(productId?)` subscription |
| `/orders` | `me.orders`, `orders(status)` (ADMIN), `placeOrder` |
| `/orders/:id` | `order(id)` (owner-or-admin), `updateOrderStatus` (ADMIN) |

## Testing

```bash
npm test       # Karma/Jasmine unit tests — no e2e layer
npm run build  # production build
```
