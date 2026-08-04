# GraphQL Demo — Caching Pattern Design

**Date:** 2026-08-03
**Status:** Approved
**Builds on:** `docs/superpowers/specs/2026-07-29-graphql-communication-protocol-demo-design.md` (original GraphQL demo — query/DataLoader/mutation/subscription patterns), `docs/superpowers/specs/2026-07-31-graphql-ecommerce-domain-design.md` (Category/Product/Order/Review domain)

## Overview

Adds a caching pattern to `communication-protocols/graphql/spring-demo`, using the `Category` tree as the subject: nothing in the schema ever mutates a category (no `addCategory`/`updateCategory`/`deleteCategory` mutation exists), which makes it a clean, honest example of cache-aside caching with no invalidation logic — every other entity in this domain (`Product.stockQty`, `Review`, `Order`) is mutated somewhere and would need eviction wiring, which is deliberately out of scope here.

The pattern demonstrates **two caching idioms** side by side, because `CategoryService` already has both shapes of method:

1. **Annotation-based caching** (`@Cacheable`) for the single-key lookup `findCategory(Long id)` — the textbook case.
2. **Manual cache-aside** for the two batch methods, `findByIds(List<Long> ids)` and `findChildrenByParentIds(List<Long> parentIds)` — because a plain `@Cacheable` on a method keyed by "the whole incoming id list" would almost never hit (the list of ids varies practically every call), which is itself worth calling out as a common caching mistake. The correct approach is a per-key check inside the batch method: look up each id individually, only query the database for the misses, then populate the cache for those.

Caching backend is Caffeine, in-process — no new docker infrastructure, consistent with this module's "no external infrastructure required" status and with other no-infra demos in this repo (`template-engines/`, `distributed-transactions/saga/`).

`listCategories` (the top-level paginated `categories` query) is explicitly **not** cached — see Scope limits.

## Dependencies

`graphql/spring-demo/pom.xml` gains:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

## Implementation

### `config/CacheConfig.java`

```java
@Configuration
@EnableCaching
public class CacheConfig {

    static final String CATEGORIES_BY_ID = "categoriesById";
    static final String CATEGORY_CHILDREN = "categoryChildren";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(CATEGORIES_BY_ID, CATEGORY_CHILDREN);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(Duration.ofMinutes(5)));
        return manager;
    }
}
```

Both caches share one `Caffeine` spec (500-entry cap, 5-minute TTL). The TTL is not a response to any in-app write path — categories are never mutated — it's a safety net for out-of-band data changes (e.g. someone edits Postgres directly), and doubles as a demonstration of Caffeine's time-based expiry, a caching concept worth showing even where this schema doesn't strictly require it.

### `CategoryService` changes

Constructor gains a `CacheManager cacheManager` dependency, from which it resolves the two `Cache` beans once (fields, `private final`). A new `AtomicInteger dbLoadCount` mirrors the existing `ReviewService.batchCallCount` idiom — incremented exactly once per underlying `categoryRepository` call site actually reached (i.e., only on a cache miss), exposed via a test-only `getDbLoadCount()`. Like `getBatchCallCount()` today, this is never wired into the GraphQL schema — it's a test hook, autowired directly in integration tests.

```java
@Cacheable(cacheNames = CacheConfig.CATEGORIES_BY_ID, key = "#id")
public Optional<Category> findCategory(Long id) {
    dbLoadCount.incrementAndGet();
    return categoryRepository.findById(id).map(CategoryService::toCategory);
}
```

`@Cacheable` on a method returning `Optional` is fine — Spring's cache abstraction unwraps/rewraps `Optional` automatically (`NullValue` handling), same as it would for a bare nullable return.

```java
public Map<Long, Category> findByIds(List<Long> ids) {
    Cache cache = cacheManager.getCache(CacheConfig.CATEGORIES_BY_ID);
    Map<Long, Category> result = new LinkedHashMap<>();
    List<Long> misses = new ArrayList<>();
    for (Long id : ids) {
        Category cached = cache.get(id, Category.class);
        if (cached != null) {
            result.put(id, cached);
        } else {
            misses.add(id);
        }
    }
    if (!misses.isEmpty()) {
        dbLoadCount.incrementAndGet();
        for (CategoryEntity entity : categoryRepository.findAllById(misses)) {
            Category category = toCategory(entity);
            cache.put(entity.getId(), category);
            result.put(entity.getId(), category);
        }
    }
    return result;
}
```

Because this uses the *same* `categoriesById` cache and the *same* key type (`Long`) as the `@Cacheable` on `findCategory`, the two are interchangeable: a category warmed via `findCategory` is a hit in `findByIds` and vice versa. This is the one deliberate cross-idiom sharing point in the design.

```java
public Map<Long, List<Category>> findChildrenByParentIds(List<Long> parentIds) {
    Cache cache = cacheManager.getCache(CacheConfig.CATEGORY_CHILDREN);
    Map<Long, List<Category>> result = new LinkedHashMap<>();
    List<Long> misses = new ArrayList<>();
    for (Long parentId : parentIds) {
        List<Category> cached = cache.get(parentId, List.class);
        if (cached != null) {
            result.put(parentId, cached);
        } else {
            misses.add(parentId);
        }
    }
    if (!misses.isEmpty()) {
        dbLoadCount.incrementAndGet();
        Map<Long, List<Category>> loaded = categoryRepository.findByParentIdIn(misses).stream()
                .map(CategoryService::toCategory).collect(Collectors.groupingBy(Category::parentId));
        for (Long parentId : misses) {
            List<Category> children = loaded.getOrDefault(parentId, List.of());
            cache.put(parentId, children);
            result.put(parentId, children);
        }
    }
    return result;
}
```

`categoryChildren` is a separate cache from `categoriesById` because the value shape differs (`List<Category>` per parent vs. a single `Category`) — sharing one cache would require namespacing keys to avoid an `Long` id colliding between "category 5" and "children of category 5", which adds complexity for no benefit here.

`listCategories(...)` and `DemoController` are unchanged.

## Data flow

**Single-key hit** (`category(id: 5)` requested twice in the same process): call 1 → `findCategory(5)` → cache miss → repository query → `dbLoadCount` 0→1 → result cached under key `5`. Call 2 → `findCategory(5)` → cache hit, method body never runs → `dbLoadCount` stays 1.

**Batch hit/miss mix** (`categoryChildren` DataLoader invoked with parent ids `[1,2,3]`, where 1 is already cached from a prior request): `findChildrenByParentIds([1,2,3])` finds 1 cached, misses `[2,3]` → one `findByParentIdIn([2,3])` call → `dbLoadCount` +1 → cache populated for 2 and 3 → merged map returned, hits and misses combined, in original input order.

**Cross-idiom hit** (`category(id: 7)` was queried earlier, warming `categoriesById[7]`; later, some other category's `parent` resolves to id 7 via the `Category.parent` `@BatchMapping`): `findByIds([7, ...])` finds 7 already cached from the earlier `findCategory` call — no repository call for that id.

## Error handling

No new failure modes. Caffeine is in-process — no network calls, nothing to time out or retry. Every cache-aside path falls back to exactly the repository call the code already makes today; a full cache miss is simply today's behavior. This module doesn't use `FailureSimulator` (that's a message-broker convention, not used elsewhere in `communication-protocols/`), so no artificial failure injection is added.

## Testing

- **Existing `CategoryServiceTest`** is unchanged. It constructs `new CategoryService(categoryRepository)` directly (no Spring AOP proxy), so `@Cacheable` is inert there — it continues to test raw mapping/pagination logic, which caching doesn't alter.
- **New `CategoryServiceCachingTest`**: boots a minimal Spring context (`@Import(CacheConfig.class)`, real `CategoryService` bean so `@Cacheable` is proxied) backed by the same `@DataJpaTest` H2 setup as `CategoryServiceTest`. Cases:
  - `findCategory` called twice with the same id → `getDbLoadCount()` increments only once.
  - `findByIds` called with an id already warmed by a prior `findCategory` call → no additional DB load for that id.
  - `findChildrenByParentIds` called twice with the same parent id → increments once; called with a mix of a cached and an uncached parent id → increments once more, covering only the miss.
  - TTL expiry: not covered by an automated test (a 5-minute real-time wait is impractical and a fake clock isn't worth the complexity for a demo) — called out in the README instead, matching how the pagination addendum documented its own scope limits in prose rather than in tests.
- **One `DemoIntegrationTest` case**, matching the existing `reviewService.getBatchCallCount()` assertion style: two GraphQL requests that both resolve the same category's children only cause one `categoryRepository` hit, asserted via `categoryService.getDbLoadCount()`.

## Docs

`communication-protocols/graphql/README.md` and `spring-demo/README.md` get a new caching section, matching the style of the existing pagination/security sections: which methods are cached and why, the two-idiom explanation (annotation vs. manual cache-aside for batch methods), the TTL/no-eviction rationale, and an explicit note that the 5-minute TTL is not exercised by the test suite.

## Scope limits

- Only `Category` reads are cached. `Product`, `Review`, `Order`, `User` are all mutated somewhere in this schema and are out of scope — caching them would require `@CacheEvict` wiring into `OrderService`/`ReviewService`, a different (eviction-focused) lesson deliberately left for a future addendum.
- `listCategories` (the top-level paginated `categories` query) is not cached — it would add a third key shape (`first`+`after` composite) without teaching anything the id-keyed lookups don't already cover.
- No `@CacheEvict`/manual eviction anywhere — the only staleness bound is the 5-minute TTL, which is not asserted by an automated test (see Testing).
- Single-process, in-memory cache (Caffeine, not Redis) — consistent with this module's "no external infrastructure required" status. A distributed/shared-cache variant (Redis) is a possible future addendum, not this one.
- Cache size (500) and TTL (5 min) are fixed constants, not configurable per-deployment — consistent with this repo's "local demo" scope elsewhere (e.g. `FAILURE_RATE` in `FailureSimulator`, the pagination addendum's `first` cap/default).
