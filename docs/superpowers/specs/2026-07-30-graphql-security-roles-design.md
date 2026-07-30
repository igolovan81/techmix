# GraphQL Demo — Security & Roles Addendum Design

**Date:** 2026-07-30
**Status:** Approved
**Builds on:** `docs/superpowers/specs/2026-07-29-graphql-communication-protocol-demo-design.md` (original GraphQL demo — query/DataLoader/mutation/subscription patterns)

## Overview

Adds a 5th pattern to `communication-protocols/graphql/spring-demo`: field/operation-level authorization. This is the direct GraphQL counterpart to `backend/rest-api`'s endpoint-level security — the only other secured module in the repo — but GraphQL has a single endpoint for every operation, so authorization necessarily moves from "which URL" (REST) to "which field" (GraphQL), via Spring Security method security (`@PreAuthorize`) on `DemoController`'s `@QueryMapping`/`@MutationMapping`/`@SubscriptionMapping` methods rather than `HttpSecurity` request matchers.

Reuses `backend/rest-api`'s exact auth mechanism and demo credentials (HTTP Basic, `InMemoryUserDetailsManager`, `user`/`userPassword` → `USER`, `admin`/`adminPassword` → `ADMIN`) for a consistent "how auth works in this repo" story, and reuses the existing `DemoExceptionResolver`/`ErrorType` classification pattern (already has unused `UNAUTHORIZED`/`FORBIDDEN` values) rather than inventing new error handling.

A new `deleteReview` mutation (ADMIN-only) is added specifically because the existing schema has no natural role-differentiated action — without it, USER and ADMIN would behave identically and the demo wouldn't actually show *different* roles, just "logged in or not."

## Dependency & configuration changes

- Add `spring-boot-starter-security` to `communication-protocols/graphql/spring-demo/pom.xml`.
- New `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/config/SecurityConfig.java`:
  - `@EnableWebSecurity` + `@EnableMethodSecurity` (the latter is what `backend/rest-api` turns on but never actually uses via `@PreAuthorize` — here it does real work).
  - `SecurityFilterChain` bean: `permitAll()` on `/graphql` (covers the HTTP POST endpoint and the WebSocket upgrade request, since both share the same path) and `/graphiql`; CSRF disabled (matches `backend/rest-api`; irrelevant for a stateless Basic-auth API with no cookie-based session).
  - `InMemoryUserDetailsManager` bean with the same two users/roles as `backend/rest-api/src/main/java/com/testingai/config/SecurityConfig.java`.

Authorization for individual GraphQL operations happens entirely at the method layer (`@PreAuthorize` on `DemoController`), **not** the HTTP layer — this is the actual point being demonstrated: you cannot secure "the endpoint" in GraphQL, only individual fields, and one query can legally mix public and protected fields in the same request/response.

## Schema change

```graphql
type Mutation {
  addReview(input: AddReviewInput!): Review!
  deleteReview(id: ID!): Boolean!
}
```

`deleteReview` returns `true` if a review with that id existed and was removed, `false` otherwise (no error for "not found" — deleting something already gone is treated as a no-op success, standard idempotent-delete semantics).

## Patterns implemented (extends the existing 4-pattern table)

| Operation | Rule | What it demonstrates |
|---|---|---|
| `products`, `product(id)` | No annotation — public | Not every field needs authorization; GraphQL lets public and protected data coexist in one schema |
| `addReview` | `@PreAuthorize("isAuthenticated()")` | Any authenticated user (USER or ADMIN) may perform this write |
| `reviewAdded` subscription | `@PreAuthorize("isAuthenticated()")` | Method security applies uniformly across query/mutation/subscription — the WebSocket transport doesn't change how authorization is expressed |
| `deleteReview(id)` *(new)* | `@PreAuthorize("hasRole('ADMIN')")` | The actual role-differentiated action — the one place USER and ADMIN behave differently |

## `ReviewService` change

Add `deleteReview(String reviewId): boolean` — scans `reviewsByProductId`'s values and removes the first matching-id entry via `List.removeIf(...)` (thread-safe on the existing `CopyOnWriteArrayList` storage, no new data structure needed). Returns whether a review was actually removed.

## Error handling

`ErrorType.UNAUTHORIZED` and `ErrorType.FORBIDDEN` (already present in the enum, unused until now) map directly onto Spring Security's two method-security failure modes:

| Exception | Classified as |
|---|---|
| Authentication-required failure (anonymous caller hits `@PreAuthorize("isAuthenticated()")`) | `UNAUTHORIZED` |
| `org.springframework.security.access.AccessDeniedException` (authenticated as USER, hits `@PreAuthorize("hasRole('ADMIN')")`) | `FORBIDDEN` |

Extend `DemoExceptionResolver.resolveToSingleError`'s existing `instanceof` chain with these two branches (same pattern already used for `IllegalArgumentException` → `BAD_REQUEST`).

Because errors are per-field, a single query like `{ products { id } deleteReview(id: "r1") }` called anonymously returns `products` data **and** an `UNAUTHORIZED` error in the same response — the same partial-failure story the module's `FailureSimulator`-driven `product(id)` query already tells, now for authorization instead of simulated infrastructure failure.

## Testing

- **`ReviewServiceTest`** — add `deleteReview` unit tests: removes a matching review and returns `true`; returns `false` for an unknown id; leaves other products' reviews untouched.
- **`DemoIntegrationTest`** — extend with the 3-tier authorization matrix over real HTTP requests (`HttpGraphQlTester.mutate().header("Authorization", "Basic " + Base64.getEncoder()...)` per credential pair):
  - anonymous: `products` succeeds; `addReview` and `deleteReview` both return an `UNAUTHORIZED` error
  - authenticated USER: `addReview` succeeds; `deleteReview` returns a `FORBIDDEN` error
  - authenticated ADMIN: both `addReview` and `deleteReview` succeed
  - one mixed-request test asserting the partial-failure shape described above (`products` data present alongside an `UNAUTHORIZED` error in the same response)
- **Subscription auth** — the exact way to attach a Basic-auth header to `WebSocketGraphQlTester`'s handshake request needs verifying against the real `spring-graphql-test` jar (same rigor as the rest of this module — no guessing from docs) before finalizing that test in the implementation plan; if no clean header-attachment API exists on the tester builder, fall back to constructing the underlying `WebSocketClient`/handshake manually.
- No changes to the existing `InventoryWorkerTest`-equivalent style tests for `DemoController`'s other methods — `products`/`product`/`addReview`'s existing unit tests in `DemoControllerTest` are unaffected since method security is enforced by the Spring AOP proxy, not by the plain Java method call `DemoControllerTest` already exercises directly (calling `controller.deleteReview(...)` directly bypasses `@PreAuthorize` entirely, same as it already does for the existing methods) — so authorization behavior can only be verified through `DemoIntegrationTest`'s real Spring context, not the plain-object `DemoControllerTest`.

## Docs

`communication-protocols/graphql/spring-demo/README.md` gets a new "Security" section: the three tiers explained, `curl -u user:userPassword ...` / `curl -u admin:adminPassword ...` examples for `addReview`/`deleteReview`, an anonymous-call example showing the `UNAUTHORIZED` error shape, and a USER-calling-`deleteReview` example showing `FORBIDDEN`.

## Scope limits

- Same demo credentials and mechanism as `backend/rest-api` (HTTP Basic, plaintext in-memory users) — not a production security guide, consistent with this repo's "local demo" scope everywhere else.
- No JWT, no OAuth2/OIDC, no password encoding.
- No per-user data ownership (e.g., "only delete your own review") — roles gate *actions* uniformly, not *data visibility* scoped to the caller.
- CSRF disabled — matches `backend/rest-api`, irrelevant for a stateless Basic-auth API.
