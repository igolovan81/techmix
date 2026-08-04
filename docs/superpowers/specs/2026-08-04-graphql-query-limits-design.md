# GraphQL Demo — Query Depth & Complexity Limiting Design

**Date:** 2026-08-04
**Status:** Approved
**Builds on:** `docs/superpowers/specs/2026-07-29-graphql-communication-protocol-demo-design.md` (original patterns), `docs/superpowers/specs/2026-07-30-graphql-pagination-filtering-design.md` (the `first`/`after` connection arguments this design's complexity weighting reuses), `docs/superpowers/specs/2026-07-31-graphql-ecommerce-domain-design.md` (the Product/Review/User/Order domain, whose type graph supplies the cyclic example below)

## Overview

Adds query depth limiting and complexity (cost) limiting to `communication-protocols/graphql/spring-demo`, closing gap #1 from `communication-protocols/graphql/GAPS.md`.

This schema has a genuine cycle: `Product.reviews → Review.author → User.orders → Order.items → OrderItem.product → Product.reviews → ...`. Nothing in the type system stops a client from walking that cycle an arbitrary number of times in one query — the motivating, real (not contrived) example for why depth/complexity limiting matters here.

Both limits are enforced using graphql-java's own built-in instrumentations (`graphql.analysis.MaxQueryDepthInstrumentation` and `MaxQueryComplexityInstrumentation`, already on the classpath transitively via `spring-boot-starter-graphql`, version 22.3) — not a hand-rolled traversal — since using the library's real, idiomatic mechanism is the point of a pattern demo.

## Config properties

New `QueryLimitsProperties` record, following the existing `SeedProperties` (`config/SeedProperties.java`) convention:

```java
@ConfigurationProperties(prefix = "app.graphql")
public record QueryLimitsProperties(int maxQueryDepth, int maxQueryComplexity) {
}
```

`application.yml` gains:

```yaml
app:
  graphql:
    max-query-depth: 15
    max-query-complexity: 5000
```

These starting values are sized generously above every legitimate query this app currently issues (deepest today: `product → reviews → edges → node → author`, 5 field levels; heaviest: the Gatling scenario's `products(first: 40) { reviews(first: 10) }`). They are **not** rigorously tuned production thresholds — the regression and introspection tests below are the actual source of truth; if either fails against these starting numbers, the number moves, not the test.

## Implementation

### Error classification

`MaxQueryDepthInstrumentation`/`MaxQueryComplexityInstrumentation` reject by throwing `graphql.execution.AbortExecutionException`, whose own `getErrorType()` is hardcoded to `graphql.ErrorType.ExecutionAborted` — a different classification than this app's `BAD_REQUEST` convention (`exception/DemoExceptionResolver.java`), and not reachable through `DemoExceptionResolver` at all, since that only resolves exceptions thrown from data fetchers, and this rejection happens in `beginExecuteOperation`, before any data fetcher runs.

Both instrumentations expose a `protected AbortExecutionException mkAbortException(...)` hook specifically so callers can customize the thrown error. `AbortExecutionException` also has a `Collection<GraphQLError> underlyingErrors` constructor — when non-empty, `graphql.GraphQL`'s abort handler (`GraphQL.java`, confirmed by reading the 22.3 sources) builds the `ExecutionResult` directly from `underlyingErrors`, bypassing `getErrorType()` entirely. So each subclass overrides `mkAbortException` to hand back one `GraphQLError` built with Spring's own `ErrorType.BAD_REQUEST`:

```java
class BadRequestMaxQueryDepthInstrumentation extends MaxQueryDepthInstrumentation {
    BadRequestMaxQueryDepthInstrumentation(int maxDepth) {
        super(maxDepth);
    }

    @Override
    protected AbortExecutionException mkAbortException(int depth, int maxDepth) {
        return new AbortExecutionException(List.of(GraphqlErrorBuilder.newError()
                .errorType(ErrorType.BAD_REQUEST)
                .message("Query depth " + depth + " exceeds maximum allowed depth " + maxDepth)
                .build()));
    }
}
```

Same shape for a `BadRequestMaxQueryComplexityInstrumentation extends MaxQueryComplexityInstrumentation`, overriding `mkAbortException(int totalComplexity, int maxComplexity)`. The client-visible error ends up with `extensions.classification: "BAD_REQUEST"` — the same shape as an `addReview` validation error — even though the mechanism (instrumentation vs. `DataFetcherExceptionResolverAdapter`) is different. `DemoExceptionResolver` itself is untouched; this is a parallel, analogous mechanism at the instrumentation layer, not a change to it.

### `QueryLimitsConfig`

```java
@Configuration
public class QueryLimitsConfig {

    @Bean
    public Instrumentation maxQueryDepthInstrumentation(QueryLimitsProperties properties) {
        return new BadRequestMaxQueryDepthInstrumentation(properties.maxQueryDepth());
    }

    @Bean
    public Instrumentation maxQueryComplexityInstrumentation(QueryLimitsProperties properties) {
        return new BadRequestMaxQueryComplexityInstrumentation(
                properties.maxQueryComplexity(), new PaginationAwareFieldComplexityCalculator());
    }
}
```

No manual wiring into `GraphQlSource` — Spring Boot's `GraphQlAutoConfiguration` collects every `Instrumentation` bean from the context via `ObjectProvider<Instrumentation>` and adds each one (confirmed by reading `GraphQlAutoConfiguration` in `spring-boot-autoconfigure` 3.4.4), the same "just add a bean" ergonomics as `CacheConfig`/`SecurityConfig`.

### `PaginationAwareFieldComplexityCalculator`

Default graphql-java complexity is a flat `1 + childComplexity` per field — every field costs the same regardless of how much data it actually returns. This schema's connection fields (`products`, `Category.children`, `Category.products`, `Product.reviews`, `User.orders`, top-level `orders`) all take a `first` argument, so complexity should scale with it — a `products(first: 500)` query is heavier than `products(first: 5)`, but flat counting treats them identically.

```java
public class PaginationAwareFieldComplexityCalculator implements FieldComplexityCalculator {

    private static final int DEFAULT_FIRST = 10; // mirrors CursorPagination.DEFAULT_FIRST

    @Override
    public int calculate(FieldComplexityEnvironment environment, int childComplexity) {
        Object first = environment.getArguments().get("first");
        int multiplier = first instanceof Integer value ? value : DEFAULT_FIRST;
        return 1 + multiplier * childComplexity;
    }
}
```

Deliberately **not** clamped to `CursorPagination.MAX_FIRST` (50): the complexity check runs on the client's raw requested `first`, before `CursorPagination.normalizeFirst` ever executes. A client can't dodge the complexity budget by relying on that downstream clamp — the whole point of the check is to reject the request before any resolver (and thus before the clamp) runs. This also means the multiplier applies across the field's whole subtree (`edges`/`node` and sibling fields like `pageInfo`/`totalCount` alike) rather than precisely down the `edges.node` path — a deliberate over-approximation, simple enough for a demo, and safe in the conservative direction (it only ever overstates cost).

## Data flow

**Legitimate query passes:** `product(id) { reviews(first: 10) { edges { node { author { displayName } } } } }` — depth and complexity both comfortably under the configured limits, executes exactly as today.

**Depth violation:** a query walking `Product.reviews.author.orders.items.product...` around the cycle enough times to exceed `max-query-depth` is rejected in `beginExecuteOperation`, before any `DataFetcher` (and therefore any repository call) runs. Response: `data: null`, one `BAD_REQUEST` error stating the measured depth and the limit.

**Complexity violation:** `products(first: 10000) { reviews(first: 10000) { edges { node { id } } } }` computes a total complexity far past `max-query-complexity` and is rejected the same way, before touching the database.

## Testing

New `QueryLimitsTest` (integration-style, same `HttpGraphQlTester` setup as `DemoIntegrationTest`):

- **Regression** — every representative existing query shape (product+reviews+author, categories+children, category+products, order+items+product, `orders(status)`+user, and the Gatling scenario's exact query) still succeeds under the configured limits.
- **Introspection regression** — GraphiQL's full introspection query (the one the `/graphiql` UI actually issues) still succeeds. This is the real risk case: introspection's `__Type.fields.type.ofType.ofType...` chains are naturally deep, and neither instrumentation exempts introspection by default (confirmed by reading the 22.3 source — no such check exists), so this must be verified, not assumed.
- **Depth rejection** — a query built around the `Product→...→Product` cycle, one level past `max-query-depth`, is rejected with a `BAD_REQUEST` error and `data: null`.
- **Complexity rejection** — a wide-`first` query one unit past `max-query-complexity` is rejected the same way.
- **Boundary correctness** — a query at exactly the configured limit succeeds; one field level (or one complexity unit) past it fails — confirms the `>` (not `>=`) comparison graphql-java uses internally.

New `QueryLimitsPropertiesTest` (or a case added to an existing config-binding test) confirms `app.graphql.max-query-depth`/`max-query-complexity` bind correctly.

## Docs

- `communication-protocols/graphql/README.md`: remove "no query depth/complexity limiting" from the Scope section; add a short subsection (matching the Pros/Cons/Typical-use-cases style of the six existing patterns) explaining the depth cycle example, the pagination-aware complexity weighting, and that both are demo-scale starting values, not tuned production thresholds.
- `communication-protocols/graphql/GAPS.md`: remove gap #1 (implemented) and drop the now-stale "no query depth/complexity limiting" line from the intro paragraph that cross-references the README Scope section.

## Scope limits

- Complexity weighting only accounts for the `first` argument multiplier — no per-field custom weights (e.g. making `reviews` costlier than `categories`).
- Limits are fixed `application.yml` values, not runtime-configurable via GraphQL itself or per-client/per-role.
- No rate limiting or query allow-listing — a separate, still-open item in `GAPS.md`, not addressed here.
- No special-cased exemption for introspection queries in code — they simply have to fit under the chosen limits, verified by the introspection regression test, not carved out.
- The `first`-multiplier approximates cost across a connection field's whole subtree, not strictly the `edges.node` path — acceptable over-approximation for a demo, called out above rather than hidden.
