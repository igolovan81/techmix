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

`ErrorType.UNAUTHORIZED` and `ErrorType.FORBIDDEN` (already present in the enum, unused until now) map onto Spring Security's method-security failures — but both "not authenticated" and "wrong role" throw the **same** exception type (`org.springframework.security.authorization.AuthorizationDeniedException`, which extends `AccessDeniedException` — verified against the `spring-security-core` jar), so `DemoExceptionResolver` can't distinguish them by `instanceof` alone the way `IllegalArgumentException` → `BAD_REQUEST` already does. It instead inspects the current `SecurityContextHolder`'s `Authentication` when it catches an `AccessDeniedException`: if it's `null` or an `AnonymousAuthenticationToken` (no real credentials presented) → `UNAUTHORIZED`; otherwise (a genuinely authenticated principal, just missing the required role) → `FORBIDDEN`.

Because errors are per-field, a single **mutation** selecting both `addReview` and `deleteReview` in one operation, called as an authenticated USER (not ADMIN), returns `addReview`'s data **and** a `FORBIDDEN` error for `deleteReview` in the same response — the same partial-failure story the module's `FailureSimulator`-driven `product(id)` query already tells, now for authorization instead of simulated infrastructure failure. (A query mixing `products` with a mutation field in one operation, as originally sketched here, isn't valid GraphQL — an operation has exactly one root type — so the demonstration uses two mutation-type fields together instead.)

## Testing

- **`ReviewServiceTest`** — add `deleteReview` unit tests: removes a matching review and returns `true`; returns `false` for an unknown id; leaves other products' reviews untouched.
- **`DemoIntegrationTest`** — extend with the 3-tier authorization matrix over real HTTP requests (`HttpGraphQlTester.mutate().header("Authorization", "Basic " + Base64.getEncoder()...)` per credential pair):
  - anonymous: `products` succeeds; `addReview` and `deleteReview` both return an `UNAUTHORIZED` error
  - authenticated USER: `addReview` succeeds; `deleteReview` returns a `FORBIDDEN` error
  - authenticated ADMIN: both `addReview` and `deleteReview` succeed
  - one mixed-mutation test (both `addReview` and `deleteReview` selected in the same operation, called as USER) asserting the partial-failure shape described above
  - anonymous subscription attempt on `reviewAdded` is rejected — but see the note below on exactly what that rejection looks like
- **Subscription auth** — `WebSocketGraphQlTester.Builder` extends `WebGraphQlTester.Builder`, which exposes `.header(String, String...)` (verified against the `spring-graphql-test` jar), so a Basic-auth header attaches the same way as on `HttpGraphQlTester`. `@PreAuthorize` on the Flux-returning `reviewAdded` method behaves synchronously exactly like the query/mutation methods (not deferred to subscribe-time) because this module's `SecurityConfig` uses plain `@EnableMethodSecurity`, not `@EnableReactiveMethodSecurity` — the latter is a separate, explicitly-opted-into annotation in Spring Security, not something that activates automatically just because a method returns `Flux`/`Mono` (verified against Spring Security's reference docs). **However**, an actual live run (a temporary probe built and discarded during implementation planning) showed that when `@SubscriptionMapping` throws before ever returning a `Publisher`, Spring GraphQL does **not** route the exception through `DemoExceptionResolver` the way it does for query/mutation fields — the client instead receives a generic `SubscriptionErrorException` ("Subscription error", classified `INTERNAL_ERROR`), regardless of what our resolver would otherwise classify it as. This held true even after wiring up the `AccessDeniedException` handling described above, confirming it's bypassed entirely for this failure mode, not just unclassified. The anonymous-subscription test asserts on this actual behavior (a `SubscriptionErrorException` terminates the Flux) rather than on `UNAUTHORIZED`, and this is documented as a scope-limit/gotcha in the module README rather than treated as a bug to fix.
- No changes to the existing `InventoryWorkerTest`-equivalent style tests for `DemoController`'s other methods — `products`/`product`/`addReview`'s existing unit tests in `DemoControllerTest` are unaffected since method security is enforced by the Spring AOP proxy, not by the plain Java method call `DemoControllerTest` already exercises directly (calling `controller.deleteReview(...)` directly bypasses `@PreAuthorize` entirely, same as it already does for the existing methods) — so authorization behavior can only be verified through `DemoIntegrationTest`'s real Spring context, not the plain-object `DemoControllerTest`.

## Docs

`communication-protocols/graphql/spring-demo/README.md` gets a new "Security" section: the three tiers explained, `curl -u user:userPassword ...` / `curl -u admin:adminPassword ...` examples for `addReview`/`deleteReview`, an anonymous-call example showing the `UNAUTHORIZED` error shape, and a USER-calling-`deleteReview` example showing `FORBIDDEN`.

## Scope limits

- Same demo credentials and mechanism as `backend/rest-api` (HTTP Basic, plaintext in-memory users) — not a production security guide, consistent with this repo's "local demo" scope everywhere else.
- No JWT, no OAuth2/OIDC, no password encoding.
- No per-user data ownership (e.g., "only delete your own review") — roles gate *actions* uniformly, not *data visibility* scoped to the caller.
- CSRF disabled — matches `backend/rest-api`, irrelevant for a stateless Basic-auth API.
- Subscription-establishment authorization failures surface to the client as a generic `SubscriptionErrorException`/`INTERNAL_ERROR`, not our classified `UNAUTHORIZED` — a genuine Spring GraphQL limitation (verified live), not something this demo works around; documented as its own gotcha alongside the file-upload one already called out in the original module's scope.
