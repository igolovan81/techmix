# Gaps

Patterns and production concerns this module does **not** demonstrate, ranked by teaching value. The [README](README.md#scope) already calls out two of these (persisted queries, federation) as deliberately out of scope; this document expands that list and adds effort estimates for anyone picking up the next incremental commit.

Confirmed by reading the code, not assumed: `csrf.disable()` in `SecurityConfig.java` alongside session-cookie auth that subscriptions rely on; `spring.graphql.graphiql.enabled: true` with no per-environment introspection toggle; the schema uses raw `Int`/`String` everywhere (no custom scalars, no `union`/`interface`, no `@defer`/`@stream`/`@skip`/`@include`, no `@deprecated`); no `jakarta.validation` annotations on any mutation input (`DemoExceptionResolver` classifies plain `IllegalArgumentException` instead); the Angular Apollo client (`apollo.provider.ts`) uses `GraphQLWsLink` plus a bare `InMemoryCache()` — no `typePolicies`, no persisted-queries link, no batch-http-link; mutations splice component signals by hand instead of using Apollo `optimisticResponse`.

## High-value gaps

| # | Gap | Why it matters | Effort |
|---|---|---|---|
| 1 | **CSRF disabled alongside cookie-session auth** | `SecurityConfig.java` calls `csrf.disable()`, but the session cookie is load-bearing for WebSocket subscription auth elsewhere in the same app — that combination is a real vulnerability pattern worth demonstrating (or fixing) rather than leaving silent. | Small–medium, high security-teaching value |
| 2 | **Custom scalars** | `priceCents`/`stockQty` are raw `Int`, `placedAt` is raw `String`, instead of `Money`/`DateTime` scalars. Common real-world pattern, cheap to add. | Small |
| 3 | **Bean validation on mutation inputs** | `AddReviewInput`/`PlaceOrderInput` rely on manual `IllegalArgumentException` checks instead of `@Valid`/`@NotBlank`/`@Min`. The idiomatic Spring GraphQL pattern maps validation failures to a distinct error type automatically. | Small |

## Medium-value gaps

| # | Gap | Why it matters | Effort |
|---|---|---|---|
| 4 | **Automatic Persisted Queries (APQ)** | No payload-size optimization on either side; standard in production Apollo deployments. | Medium |
| 5 | **Optimistic UI / normalized cache** | Angular mutations wait for the round trip and manually patch signals instead of using Apollo `optimisticResponse`/`typePolicies` (the client uses a bare `InMemoryCache()`). Would teach real Apollo cache mechanics. | Medium |
| 6 | **Introspection disabling per environment** | GraphiQL/introspection stays on unconditionally; a trivial profile-conditional wire-up teaches a real prod-hardening step that's currently missing entirely. | Small |
| 7 | **Tracing/APM integration** | No OpenTelemetry or Apollo-tracing spans around resolvers — no observability story for a request's resolver-by-resolver cost. | Medium |

## Lower priority for this repo

| # | Gap | Why it's lower priority |
|---|---|---|
| 8 | **`@defer`/`@stream` incremental delivery** | Newer spec features; `graphql-java` support is version-sensitive, raising effort for uncertain teaching payoff. |
| 9 | **Unions/interfaces** | No polymorphic schema modeling (e.g. a `SearchResult = Product \| Category` union) — doesn't naturally fit the current e-commerce domain. |
| 10 | **Batched HTTP requests / schema federation** | Already named out of scope in the [README](README.md#scope). Federation would need a second subgraph; low value for a single-service teaching demo. |

## Suggested next pick

**#1** (the CSRF/session-auth gap) — a genuine production concern the codebase currently handles silently wrong, unlike most of the medium/lower items, which are simply APIs not yet exercised.
