# GraphQL Demo — Pagination & Filtering Addendum Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a 6th GraphQL pattern to `communication-protocols/graphql/spring-demo`: Relay-style cursor pagination and input-object filtering on the `products` query and the `Product.reviews` batch-mapped field.

**Architecture:** A generic `Connection<T>`/`Edge<T>`/`PageInfo` record trio plus a `CursorPagination.paginate(List<T>, Integer first, String after)` utility (new `pagination` package) backs both `ProductConnection` and `ReviewConnection` in the schema — one implementation, reused by reflection-based GraphQL field resolution. `ProductCatalogService` and `ReviewService` each gain a filtered-listing overload; `DemoController`'s `products` query and `reviews` batch mapping wrap the filtered lists with `CursorPagination.paginate`.

**Tech Stack:** Spring Boot 3.4.4, Spring GraphQL 1.3.4, Java 21 (records), JUnit 5, AssertJ, `spring-graphql-test`.

## Global Constraints

- `products` moves from `[Product!]!` to `ProductConnection!`; `Product.reviews` moves from `[Review!]!` to `ReviewConnection!` — this is a breaking schema change, and every existing test/load-script query against these fields must be updated to the new shape, not left broken.
- Pagination is **forward-only**: `first`/`after` only, no `last`/`before`.
- `first` defaults to 10 when omitted and is clamped to a max of 50 when the caller asks for more; `first <= 0` is rejected with `IllegalArgumentException`.
- Cursors are Base64 of `"cursor:<index>"` — a position in the filtered, ordered list at the time the cursor was issued. A cursor from one filter is not guaranteed valid against a different filter (documented as a caveat, not guarded against in code).
- One generic `Connection<T>`/`Edge<T>`/`PageInfo` implementation (new package `com.testingai.graphql.pagination`) backs both `ProductConnection` and `ReviewConnection` — do not create separate `ProductConnection`/`ReviewConnection` Java classes.
- `ProductFilter` fields (`nameContains`, `minPriceCents`, `maxPriceCents`) and `ReviewFilter`'s `minRating` are all optional/nullable and ANDed together; a null filter argument means no filtering at all.
- Existing no-arg `ProductCatalogService.listProducts()` and `ReviewService.findByProductIds(List<String>)` must keep working unmodified for internal callers (review seeding, `findProduct`, post-mutation test lookups) — add filtered overloads rather than changing existing signatures.
- Build/test commands (per `CLAUDE.md`): from `communication-protocols/`, `mvn test -pl graphql/spring-demo -am` runs unit tests; `mvn gatling:test -pl graphql/spring-demo` and `mvn verify -Pjmeter-load-test -pl graphql/spring-demo` run the load tests (app must be running first). Use `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home` for every Maven command — this machine's default `java` resolves to JDK 25, which breaks this repo's Lombok/Groovy-based tooling.

---

### Task 1: Cursor pagination utility

**Files:**
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/pagination/PageInfo.java`
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/pagination/Edge.java`
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/pagination/Connection.java`
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/pagination/CursorPagination.java`
- Test: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/pagination/CursorPaginationTest.java`

**Interfaces:**
- Consumes: nothing — pure utility, no dependency on the rest of the module.
- Produces: `record PageInfo(boolean hasNextPage, String endCursor)`, `record Edge<T>(T node, String cursor)`, `record Connection<T>(List<Edge<T>> edges, PageInfo pageInfo, int totalCount)`, `static <T> Connection<T> CursorPagination.paginate(List<T> items, Integer first, String after)` — used by Task 5 (`DemoController`).

- [ ] **Step 1: Write the failing tests**

Create `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/pagination/CursorPaginationTest.java`:

```java
package com.testingai.graphql.pagination;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorPaginationTest {

	@Test
	void paginate_returnsEmptyConnection_forEmptyList() {
		Connection<String> connection = CursorPagination.paginate(List.of(), null, null);

		assertThat(connection.edges()).isEmpty();
		assertThat(connection.pageInfo().hasNextPage()).isFalse();
		assertThat(connection.pageInfo().endCursor()).isNull();
		assertThat(connection.totalCount()).isZero();
	}

	@Test
	void paginate_returnsFullList_whenFirstExceedsListSize() {
		List<String> items = List.of("a", "b", "c");

		Connection<String> connection = CursorPagination.paginate(items, 10, null);

		assertThat(connection.edges()).extracting(Edge::node).containsExactly("a", "b", "c");
		assertThat(connection.pageInfo().hasNextPage()).isFalse();
		assertThat(connection.totalCount()).isEqualTo(3);
	}

	@Test
	void paginate_stopsExactlyAtPageBoundary_andCursorAdvancesToNextPage() {
		List<String> items = List.of("a", "b", "c", "d");

		Connection<String> firstPage = CursorPagination.paginate(items, 2, null);

		assertThat(firstPage.edges()).extracting(Edge::node).containsExactly("a", "b");
		assertThat(firstPage.pageInfo().hasNextPage()).isTrue();
		assertThat(firstPage.pageInfo().endCursor()).isNotNull();

		Connection<String> secondPage = CursorPagination.paginate(items, 2, firstPage.pageInfo().endCursor());

		assertThat(secondPage.edges()).extracting(Edge::node).containsExactly("c", "d");
		assertThat(secondPage.pageInfo().hasNextPage()).isFalse();
		assertThat(secondPage.totalCount()).isEqualTo(4);
	}

	@Test
	void paginate_returnsEmptyPage_whenAfterPointsPastLastItem() {
		List<String> items = List.of("a", "b");

		Connection<String> lastPage = CursorPagination.paginate(items, 10, null);
		Connection<String> pastEnd = CursorPagination.paginate(items, 10, lastPage.pageInfo().endCursor());

		assertThat(pastEnd.edges()).isEmpty();
		assertThat(pastEnd.pageInfo().hasNextPage()).isFalse();
		assertThat(pastEnd.pageInfo().endCursor()).isNull();
	}

	@Test
	void paginate_throws_whenCursorIsMalformed() {
		assertThatThrownBy(() -> CursorPagination.paginate(List.of("a"), 10, "not-a-valid-cursor!!"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void paginate_defaultsFirstToTen_whenOmitted() {
		List<String> items = IntStream.range(0, 12).mapToObj(String::valueOf).toList();

		Connection<String> connection = CursorPagination.paginate(items, null, null);

		assertThat(connection.edges()).hasSize(10);
		assertThat(connection.pageInfo().hasNextPage()).isTrue();
	}

	@Test
	void paginate_clampsFirstToFiftyMax() {
		List<String> items = IntStream.range(0, 60).mapToObj(String::valueOf).toList();

		Connection<String> connection = CursorPagination.paginate(items, 1000, null);

		assertThat(connection.edges()).hasSize(50);
		assertThat(connection.pageInfo().hasNextPage()).isTrue();
	}

	@Test
	void paginate_throws_whenFirstIsNotPositive() {
		assertThatThrownBy(() -> CursorPagination.paginate(List.of("a"), 0, null))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn test -pl graphql/spring-demo -am -Dtest=CursorPaginationTest` (from `communication-protocols/`)

Expected: FAIL — compilation error, `Connection`/`Edge`/`CursorPagination` don't exist yet.

- [ ] **Step 3: Implement the records and the utility**

Create `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/pagination/PageInfo.java`:

```java
package com.testingai.graphql.pagination;

public record PageInfo(boolean hasNextPage, String endCursor) {
}
```

Create `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/pagination/Edge.java`:

```java
package com.testingai.graphql.pagination;

public record Edge<T>(T node, String cursor) {
}
```

Create `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/pagination/Connection.java`:

```java
package com.testingai.graphql.pagination;

import java.util.List;

public record Connection<T>(List<Edge<T>> edges, PageInfo pageInfo, int totalCount) {
}
```

Create `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/pagination/CursorPagination.java`:

```java
package com.testingai.graphql.pagination;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Relay-style forward-only cursor pagination shared by {@code ProductConnection} and {@code ReviewConnection} — one
 * generic implementation, since GraphQL field resolution goes by property access (edges/node/cursor/pageInfo), not
 * by matching Java class names to GraphQL type names.
 *
 * <p>A cursor encodes a position in the caller's filtered, ordered list (Base64 of {@code "cursor:<index>"}), so a
 * cursor issued under one filter is not guaranteed valid against a different filter — the caller gets whatever
 * position that index decodes to.
 */
public final class CursorPagination {

	private static final String CURSOR_PREFIX = "cursor:";
	private static final int DEFAULT_FIRST = 10;
	private static final int MAX_FIRST = 50;

	private CursorPagination() {
	}

	public static <T> Connection<T> paginate(List<T> items, Integer first, String after) {
		int startIndex = after == null ? 0 : decodeCursor(after) + 1;
		int limit = normalizeFirst(first);
		List<T> page = items.stream().skip(startIndex).limit(limit).toList();
		List<Edge<T>> edges = IntStream.range(0, page.size())
				.mapToObj(i -> new Edge<>(page.get(i), encodeCursor(startIndex + i))).toList();
		boolean hasNextPage = startIndex + page.size() < items.size();
		String endCursor = edges.isEmpty() ? null : edges.getLast().cursor();
		return new Connection<>(edges, new PageInfo(hasNextPage, endCursor), items.size());
	}

	private static int normalizeFirst(Integer first) {
		if (first == null) {
			return DEFAULT_FIRST;
		}
		if (first <= 0) {
			throw new IllegalArgumentException("first must be positive, got " + first);
		}
		return Math.min(first, MAX_FIRST);
	}

	private static String encodeCursor(int index) {
		return Base64.getEncoder().encodeToString((CURSOR_PREFIX + index).getBytes(StandardCharsets.UTF_8));
	}

	private static int decodeCursor(String cursor) {
		String decoded;
		try {
			decoded = new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Malformed cursor: " + cursor, e);
		}
		if (!decoded.startsWith(CURSOR_PREFIX)) {
			throw new IllegalArgumentException("Malformed cursor: " + cursor);
		}
		try {
			return Integer.parseInt(decoded.substring(CURSOR_PREFIX.length()));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Malformed cursor: " + cursor, e);
		}
	}
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn test -pl graphql/spring-demo -am -Dtest=CursorPaginationTest` (from `communication-protocols/`)

Expected: PASS (all 8 tests).

- [ ] **Step 5: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/pagination/ \
        communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/pagination/
git commit -m "feat(communication-protocols): add generic Relay-style cursor pagination utility"
```

---

### Task 2: `ProductFilter` and `ProductCatalogService` filtering

**Files:**
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/ProductFilter.java`
- Modify: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/ProductCatalogService.java`
- Test: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/domain/ProductCatalogServiceTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `record ProductFilter(String nameContains, Integer minPriceCents, Integer maxPriceCents)`, `ProductCatalogService.listProducts(ProductFilter filter): List<Product>` — used by Task 5 (`DemoController.products`).

- [ ] **Step 1: Write the failing tests**

In `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/domain/ProductCatalogServiceTest.java`, add:

```java
	@Test
	void listProducts_withNullFilter_returnsFullCatalog() {
		assertThat(service.listProducts(null)).hasSize(40);
	}

	@Test
	void listProducts_filtersByNameContains_caseInsensitive() {
		List<Product> filtered = service.listProducts(new ProductFilter("widget", null, null));

		assertThat(filtered).isNotEmpty().allSatisfy(
				product -> assertThat(product.name().toLowerCase()).contains("widget"));
	}

	@Test
	void listProducts_filtersByPriceRange() {
		List<Product> filtered = service.listProducts(new ProductFilter(null, 1000, 2000));

		assertThat(filtered).isNotEmpty()
				.allSatisfy(product -> assertThat(product.priceCents()).isBetween(1000L, 2000L));
	}

	@Test
	void listProducts_combinesNameAndPriceFilters() {
		// "widget" alone matches 4 products across all variants (Mini/Standard/Pro/Max Widget, priced 636/2006/3376/4746
		// in this catalog's deterministic pricing formula); adding the price range narrows it to just "Standard Widget".
		List<Product> nameOnly = service.listProducts(new ProductFilter("widget", null, null));
		List<Product> combined = service.listProducts(new ProductFilter("widget", 1000, 3000));

		assertThat(combined).isNotEmpty();
		assertThat(combined).allSatisfy(product -> {
			assertThat(product.name().toLowerCase()).contains("widget");
			assertThat(product.priceCents()).isBetween(1000L, 3000L);
		});
		assertThat(combined.size()).isLessThanOrEqualTo(nameOnly.size());
	}
```

Add this import at the top of the test file, alongside the existing `org.junit.jupiter.api.Test` import:

```java
import java.util.List;
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn test -pl graphql/spring-demo -am -Dtest=ProductCatalogServiceTest` (from `communication-protocols/`)

Expected: FAIL — compilation error, `ProductFilter` and `listProducts(ProductFilter)` don't exist yet.

- [ ] **Step 3: Implement `ProductFilter` and the filtered listing method**

Create `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/ProductFilter.java`:

```java
package com.testingai.graphql.domain;

public record ProductFilter(String nameContains, Integer minPriceCents, Integer maxPriceCents) {
}
```

In `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/ProductCatalogService.java`, add this method directly after `listProducts()`:

```java
	public List<Product> listProducts(ProductFilter filter) {
		return products.stream().filter(product -> matches(product, filter)).toList();
	}

	private static boolean matches(Product product, ProductFilter filter) {
		if (filter == null) {
			return true;
		}
		if (filter.nameContains() != null
				&& !product.name().toLowerCase().contains(filter.nameContains().toLowerCase())) {
			return false;
		}
		if (filter.minPriceCents() != null && product.priceCents() < filter.minPriceCents()) {
			return false;
		}
		return filter.maxPriceCents() == null || product.priceCents() <= filter.maxPriceCents();
	}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn test -pl graphql/spring-demo -am -Dtest=ProductCatalogServiceTest` (from `communication-protocols/`)

Expected: PASS (all tests — 3 pre-existing plus 4 new).

- [ ] **Step 5: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/ProductFilter.java \
        communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/ProductCatalogService.java \
        communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/domain/ProductCatalogServiceTest.java
git commit -m "feat(communication-protocols): add ProductFilter and ProductCatalogService.listProducts(filter)"
```

---

### Task 3: `ReviewFilter` and `ReviewService` filtering

**Files:**
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/ReviewFilter.java`
- Modify: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/ReviewService.java`
- Test: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/domain/ReviewServiceTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `record ReviewFilter(Integer minRating)`, `ReviewService.findByProductIds(List<String> productIds, ReviewFilter filter): Map<String, List<Review>>` — used by Task 5 (`DemoController.reviews`). The existing no-arg-filter `findByProductIds(List<String>)` is kept, delegating to the new overload with a `null` filter.

- [ ] **Step 1: Write the failing tests**

In `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/domain/ReviewServiceTest.java`, add:

```java
	@Test
	void findByProductIds_withNullFilter_returnsAllReviews() {
		service.addReview("p1", "Jordan", 2, "meh");
		service.addReview("p1", "Sam", 5, "great");

		List<Review> reviews = service.findByProductIds(List.of("p1"), null).get("p1");

		assertThat(reviews).extracting(Review::rating).contains(2, 5);
	}

	@Test
	void findByProductIds_filtersByMinRating() {
		service.addReview("p1", "Jordan", 2, "meh");
		service.addReview("p1", "Sam", 5, "great");

		List<Review> reviews = service.findByProductIds(List.of("p1"), new ReviewFilter(4)).get("p1");

		assertThat(reviews).extracting(Review::rating).containsOnly(5);
	}

	@Test
	void findByProductIds_withFilter_stillBatchesInOneCall() {
		service.findByProductIds(List.of("p1", "p2", "p3"), new ReviewFilter(3));

		assertThat(service.getBatchCallCount()).isEqualTo(1);
	}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn test -pl graphql/spring-demo -am -Dtest=ReviewServiceTest` (from `communication-protocols/`)

Expected: FAIL — compilation error, `ReviewFilter` and the two-argument `findByProductIds` don't exist yet.

- [ ] **Step 3: Implement `ReviewFilter` and the filtered overload**

Create `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/ReviewFilter.java`:

```java
package com.testingai.graphql.domain;

public record ReviewFilter(Integer minRating) {
}
```

In `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/ReviewService.java`, replace the existing `findByProductIds` method (its current body increments `batchCallCount`, logs, and loops directly):

```java
	/**
	 * Batch-fetches reviews for every product id in one call — the DataLoader pattern that avoids the N+1 problem when
	 * resolving {@code Product.reviews} for a list of products in a single GraphQL query.
	 */
	public Map<String, List<Review>> findByProductIds(List<String> productIds) {
		batchCallCount.incrementAndGet();
		log.info("batch fetching reviews for {} products in one call", productIds.size());
		Map<String, List<Review>> result = new LinkedHashMap<>();
		for (String productId : productIds) {
			result.put(productId, reviewsByProductId.getOrDefault(productId, List.of()));
		}
		return result;
	}
```

with:

```java
	/**
	 * Batch-fetches reviews for every product id in one call — the DataLoader pattern that avoids the N+1 problem when
	 * resolving {@code Product.reviews} for a list of products in a single GraphQL query.
	 */
	public Map<String, List<Review>> findByProductIds(List<String> productIds) {
		return findByProductIds(productIds, null);
	}

	/**
	 * Same batching contract as {@link #findByProductIds(List)}, with an optional {@link ReviewFilter} applied to each
	 * product's list before it's returned.
	 */
	public Map<String, List<Review>> findByProductIds(List<String> productIds, ReviewFilter filter) {
		batchCallCount.incrementAndGet();
		log.info("batch fetching reviews for {} products in one call", productIds.size());
		Map<String, List<Review>> result = new LinkedHashMap<>();
		for (String productId : productIds) {
			List<Review> reviews = reviewsByProductId.getOrDefault(productId, List.of());
			result.put(productId, filterReviews(reviews, filter));
		}
		return result;
	}

	private static List<Review> filterReviews(List<Review> reviews, ReviewFilter filter) {
		if (filter == null || filter.minRating() == null) {
			return reviews;
		}
		return reviews.stream().filter(review -> review.rating() >= filter.minRating()).toList();
	}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn test -pl graphql/spring-demo -am -Dtest=ReviewServiceTest` (from `communication-protocols/`)

Expected: PASS (all tests — 6 pre-existing plus 3 new).

- [ ] **Step 5: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/ReviewFilter.java \
        communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/ReviewService.java \
        communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/domain/ReviewServiceTest.java
git commit -m "feat(communication-protocols): add ReviewFilter and filtered ReviewService.findByProductIds overload"
```

---

### Task 4: Schema change and `DemoController` wiring

**Files:**
- Modify: `communication-protocols/graphql/spring-demo/src/main/resources/graphql/schema.graphqls`
- Modify: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/controller/DemoController.java`
- Modify: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/DemoControllerTest.java`

**Interfaces:**
- Consumes: `CursorPagination.paginate` + `Connection`/`Edge`/`PageInfo` (Task 1), `ProductFilter` + `ProductCatalogService.listProducts(ProductFilter)` (Task 2), `ReviewFilter` + `ReviewService.findByProductIds(List, ReviewFilter)` (Task 3).
- Produces: `DemoController.products(ProductFilter, Integer, String): Connection<Product>`, `DemoController.reviews(List<Product>, ReviewFilter, Integer, String): Map<Product, Connection<Review>>` — used by Task 5 (`DemoIntegrationTest`) and Task 6 (load tests).

This task changes the schema's `products`/`Product.reviews` shape, so `DemoControllerTest` (calls controller methods directly, no Spring context) is fixed here, but `DemoIntegrationTest` and the load-test scripts (which go through a real GraphQL execution against the schema) will fail until Tasks 5 and 6 update them — expected, not a regression to chase down mid-task.

- [ ] **Step 1: Update the schema**

In `communication-protocols/graphql/spring-demo/src/main/resources/graphql/schema.graphqls`, replace the entire file with:

```graphql
type Product {
    id: ID!
    name: String!
    priceCents: Int!
    reviews(filter: ReviewFilter, first: Int, after: String): ReviewConnection!
}

type Review {
    id: ID!
    productId: ID!
    author: String!
    rating: Int!
    comment: String
}

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

type Mutation {
    addReview(input: AddReviewInput!): Review!
    deleteReview(id: ID!): Boolean
}

type Subscription {
    reviewAdded(productId: ID): Review!
}

input AddReviewInput {
    productId: ID!
    author: String!
    rating: Int!
    comment: String
}
```

- [ ] **Step 2: Write the failing `DemoControllerTest` changes**

In `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/DemoControllerTest.java`, replace:

```java
	@Test
	void products_returnsFullCatalog() {
		assertThat(controller.products()).hasSize(40);
	}
```

with:

```java
	@Test
	void products_returnsFullCatalog_whenNoFilterOrPagination() {
		Connection<Product> connection = controller.products(null, null, null);

		assertThat(connection.edges()).hasSize(10);
		assertThat(connection.totalCount()).isEqualTo(40);
		assertThat(connection.pageInfo().hasNextPage()).isTrue();
	}

	@Test
	void products_appliesFilter_beforePagination() {
		Connection<Product> connection = controller.products(new ProductFilter("mini", null, null), 50, null);

		assertThat(connection.edges()).extracting(edge -> edge.node().name())
				.allSatisfy(name -> assertThat(name.toLowerCase()).contains("mini"));
		assertThat(connection.pageInfo().hasNextPage()).isFalse();
	}
```

Replace:

```java
	@Test
	void reviews_batchesAllProducts_inOneCall() {
		List<Product> products = productCatalogService.listProducts().subList(0, 3);

		Map<Product, List<Review>> reviewsByProduct = controller.reviews(products);

		assertThat(reviewsByProduct).hasSize(3);
		assertThat(reviewService.getBatchCallCount()).isEqualTo(1);
	}
```

with:

```java
	@Test
	void reviews_batchesAllProducts_inOneCall() {
		List<Product> products = productCatalogService.listProducts().subList(0, 3);

		Map<Product, Connection<Review>> reviewsByProduct = controller.reviews(products, null, null, null);

		assertThat(reviewsByProduct).hasSize(3);
		assertThat(reviewService.getBatchCallCount()).isEqualTo(1);
	}

	@Test
	void reviews_appliesFilterAndPagination_perProduct() {
		controller.addReview(new AddReviewInput("p1", "Jordan", 2, "meh"));
		controller.addReview(new AddReviewInput("p1", "Sam", 5, "great"));
		List<Product> products = List.of(productCatalogService.findProduct("p1").orElseThrow());

		Map<Product, Connection<Review>> reviewsByProduct = controller.reviews(products, new ReviewFilter(4), 10, null);

		assertThat(reviewsByProduct.values()).extracting(Connection::edges)
				.allSatisfy(edges -> assertThat(edges).extracting(edge -> edge.node().rating()).containsOnly(5));
	}
```

Add these imports directly after the existing `com.testingai.graphql.domain.ReviewService` import:

```java
import com.testingai.graphql.domain.ProductFilter;
import com.testingai.graphql.domain.ReviewFilter;
import com.testingai.graphql.pagination.Connection;
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn test -pl graphql/spring-demo -am -Dtest=DemoControllerTest` (from `communication-protocols/`)

Expected: FAIL — compilation error, `controller.products(...)`/`controller.reviews(...)` don't have these signatures yet.

- [ ] **Step 4: Implement the controller changes**

In `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/controller/DemoController.java`, replace:

```java
	/**
	 * Query — returns the full in-memory catalog.
	 */
	@QueryMapping
	public List<Product> products() {
		log.info("[products] returning {} products", productCatalogService.listProducts().size());
		return productCatalogService.listProducts();
	}
```

with:

```java
	/**
	 * Query — returns a filtered, paginated page of the in-memory catalog. See {@link CursorPagination} for the
	 * pagination contract (forward-only, {@code first} defaults to 10 and is clamped to 50).
	 */
	@QueryMapping
	public Connection<Product> products(@Argument ProductFilter filter, @Argument Integer first,
			@Argument String after) {
		List<Product> filtered = productCatalogService.listProducts(filter);
		Connection<Product> page = CursorPagination.paginate(filtered, first, after);
		log.info("[products] returning {} of {} filtered products", page.edges().size(), filtered.size());
		return page;
	}
```

Replace:

```java
	/**
	 * Batch mapping for {@code Product.reviews} — the DataLoader pattern. However many products are being resolved in a
	 * single query, this method runs exactly once, fetching every product's reviews in one call to
	 * {@link ReviewService#findByProductIds(List)} instead of once per product (the N+1 problem).
	 */
	@BatchMapping
	public Map<Product, List<Review>> reviews(List<Product> products) {
		List<String> productIds = products.stream().map(Product::id).toList();
		Map<String, List<Review>> reviewsByProductId = reviewService.findByProductIds(productIds);
		return products.stream().collect(Collectors.toMap(product -> product,
				product -> reviewsByProductId.getOrDefault(product.id(), List.of())));
	}
```

with:

```java
	/**
	 * Batch mapping for {@code Product.reviews} — the DataLoader pattern. However many products are being resolved in a
	 * single query, this method runs exactly once, fetching every product's reviews in one call to
	 * {@link ReviewService#findByProductIds(List, ReviewFilter)} instead of once per product (the N+1 problem). Every
	 * product being resolved shares the same {@code filter}/{@code first}/{@code after} arguments — they come from one
	 * field selection in the query, not per-product.
	 */
	@BatchMapping
	public Map<Product, Connection<Review>> reviews(List<Product> products, @Argument ReviewFilter filter,
			@Argument Integer first, @Argument String after) {
		List<String> productIds = products.stream().map(Product::id).toList();
		Map<String, List<Review>> reviewsByProductId = reviewService.findByProductIds(productIds, filter);
		return products.stream().collect(Collectors.toMap(product -> product,
				product -> CursorPagination.paginate(reviewsByProductId.getOrDefault(product.id(), List.of()), first,
						after)));
	}
```

Add these imports directly after the existing `com.testingai.graphql.domain.ReviewService` import:

```java
import com.testingai.graphql.domain.ProductFilter;
import com.testingai.graphql.domain.ReviewFilter;
import com.testingai.graphql.pagination.Connection;
import com.testingai.graphql.pagination.CursorPagination;
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn test -pl graphql/spring-demo -am -Dtest=DemoControllerTest` (from `communication-protocols/`)

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/main/resources/graphql/schema.graphqls \
        communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/controller/DemoController.java \
        communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/DemoControllerTest.java
git commit -m "feat(communication-protocols): add pagination and filtering to products query and Product.reviews"
```

---

### Task 5: `DemoIntegrationTest` — connection shape end-to-end

**Files:**
- Modify: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/DemoIntegrationTest.java`

**Interfaces:**
- Consumes: the new schema shape from Task 4 (`ProductConnection`/`ReviewConnection`/`ProductFilter`/`ReviewFilter`).
- Produces: nothing consumed elsewhere — end-to-end proof for the whole feature.

- [ ] **Step 1: Update `query_returnsAllProducts` to the connection shape**

Replace:

```java
	@Test
	void query_returnsAllProducts() {
		graphQlTester.document("""
				query {
				  products { id name }
				}
				""").execute().path("products").entityList(Object.class).hasSize(40);
	}
```

with:

```java
	@Test
	void query_returnsFirstPageOfProducts_byDefault() {
		graphQlTester.document("""
				query {
				  products {
				    edges { node { id name } cursor }
				    pageInfo { hasNextPage endCursor }
				    totalCount
				  }
				}
				""").execute().path("products.edges").entityList(Object.class).hasSize(10).path("products.totalCount")
				.entity(Integer.class).isEqualTo(40).path("products.pageInfo.hasNextPage").entity(Boolean.class)
				.isEqualTo(true);
	}

	@Test
	void query_pagesThroughAllProducts_usingEndCursor() {
		String firstPageQuery = """
				query {
				  products(first: 15) {
				    edges { node { id } cursor }
				    pageInfo { hasNextPage endCursor }
				  }
				}
				""";

		String firstEndCursor = graphQlTester.document(firstPageQuery).execute().path("products.pageInfo.endCursor")
				.entity(String.class).get();

		graphQlTester.document("""
				query {
				  products(first: 15, after: "%s") {
				    edges { node { id } }
				    pageInfo { hasNextPage }
				  }
				}
				""".formatted(firstEndCursor)).execute().path("products.edges").entityList(Object.class).hasSize(15)
				.path("products.pageInfo.hasNextPage").entity(Boolean.class).isEqualTo(true);
	}

	@Test
	void query_filtersProductsByNameAndPriceRange() {
		graphQlTester.document("""
				query {
				  products(filter: { nameContains: "mini" }, first: 50) {
				    edges { node { name } }
				    totalCount
				  }
				}
				""").execute().path("products.edges").entityList(java.util.Map.class)
				.satisfies(edges -> assertThat(edges).isNotEmpty().allSatisfy(edge -> assertThat(
						((java.util.Map<?, ?>) edge.get("node")).get("name").toString().toLowerCase())
								.contains("mini")));
	}
```

- [ ] **Step 2: Update `query_returnsProductsWithNestedReviews_batchedInOneCall` to the connection shape**

Replace:

```java
	@Test
	void query_returnsProductsWithNestedReviews_batchedInOneCall() {
		int batchCallsBefore = reviewService.getBatchCallCount();

		graphQlTester.document("""
				query {
				  products {
				    id
				    name
				    reviews { id author rating }
				  }
				}
				""").execute().path("products").entityList(Object.class).hasSize(40);

		assertThat(reviewService.getBatchCallCount()).isEqualTo(batchCallsBefore + 1);
	}
```

with:

```java
	@Test
	void query_returnsProductsWithNestedReviews_batchedInOneCall() {
		int batchCallsBefore = reviewService.getBatchCallCount();

		graphQlTester.document("""
				query {
				  products(first: 40) {
				    edges {
				      node {
				        id
				        name
				        reviews { edges { node { id author rating } } }
				      }
				    }
				  }
				}
				""").execute().path("products.edges").entityList(Object.class).hasSize(40);

		assertThat(reviewService.getBatchCallCount()).isEqualTo(batchCallsBefore + 1);
	}

	@Test
	void query_filtersReviewsByMinRating() {
		asUser().document("""
				mutation {
				  addReview(input: { productId: "p1", author: "Jordan", rating: 2, comment: "meh" }) { id }
				}
				""").execute();
		asUser().document("""
				mutation {
				  addReview(input: { productId: "p1", author: "Sam", rating: 5, comment: "great" }) { id }
				}
				""").execute();

		graphQlTester.document("""
				query {
				  product(id: "p1") {
				    reviews(filter: { minRating: 4 }, first: 10) {
				      edges { node { rating } }
				    }
				  }
				}
				""").execute().path("product.reviews.edges").entityList(java.util.Map.class)
				.satisfies(edges -> assertThat(edges).isNotEmpty().allSatisfy(
						edge -> assertThat((Integer) ((java.util.Map<?, ?>) edge.get("node")).get("rating"))
								.isGreaterThanOrEqualTo(4)));
	}
```

- [ ] **Step 3: Update the remaining bare `products { id }` query, in `query_partiallyFails_whenProductLookupSimulatesFailure`**

This is the last remaining test still querying `products` in the old bare-list shape. Replace:

```java
		String query = """
				query {
				  products { id }
				  product(id: "p1") { id name }
				}
				""";
```

with:

```java
		String query = """
				query {
				  products { edges { node { id } } }
				  product(id: "p1") { id name }
				}
				""";
```

and replace:

```java
			afterErrors.path("product").valueIsNull();
			afterErrors.path("products").entityList(Object.class).hasSize(40);
```

with:

```java
			afterErrors.path("product").valueIsNull();
			afterErrors.path("products.edges").entityList(Object.class).hasSize(10);
```

(`products` with no `first` argument now defaults to a page of 10, not the full 40 — the assertion must reflect the new default page size, not the old full-catalog size.)

- [ ] **Step 4: Run the integration test class to verify it passes**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn test -pl graphql/spring-demo -am -Dtest=DemoIntegrationTest` (from `communication-protocols/`)

Expected: PASS (all tests — pre-existing security/mutation/subscription tests unaffected, updated product/review query tests passing against the new connection shape, new pagination/filter tests passing).

- [ ] **Step 5: Run the full module suite**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn test -pl graphql/spring-demo -am` (from `communication-protocols/`)

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/DemoIntegrationTest.java
git commit -m "test(communication-protocols): update GraphQL DemoIntegrationTest for pagination and filtering"
```

---

### Task 6: Load tests — connection shape (Gatling and JMeter)

**Files:**
- Modify: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/performance/DemoSimulation.java`
- Modify: `communication-protocols/graphql/spring-demo/src/test/jmeter/DemoSimulation.jmx`

**Interfaces:**
- Consumes: nothing new.
- Produces: nothing consumed elsewhere — leaf change.

Both load tests query `{ products { id name priceCents reviews { id author rating } } }`, which no longer matches the schema after Task 4 — both need the connection shape.

- [ ] **Step 1: Update the Gatling query body**

In `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/performance/DemoSimulation.java`, replace:

```java
			{"query":"{ products { id name priceCents reviews { id author rating } } }"}""";
```

with:

```java
			{"query":"{ products(first: 40) { edges { node { id name priceCents reviews(first: 10) { edges { node { id author rating } } } } } } }"}""";
```

- [ ] **Step 2: Run the Gatling load test to verify it still passes**

Start the app first (`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn -pl graphql/spring-demo spring-boot:run` from `communication-protocols/`, in a separate terminal), then run:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn gatling:test -pl graphql/spring-demo
```

Expected: all three requests report 0 KO in the console summary.

- [ ] **Step 3: Update the JMeter request body**

In `communication-protocols/graphql/spring-demo/src/test/jmeter/DemoSimulation.jmx`, replace:

```xml
                <stringProp name="Argument.value">{&quot;query&quot;:&quot;{ products { id name priceCents reviews { id author rating } } }&quot;}</stringProp>
```

with:

```xml
                <stringProp name="Argument.value">{&quot;query&quot;:&quot;{ products(first: 40) { edges { node { id name priceCents reviews(first: 10) { edges { node { id author rating } } } } } } }&quot;}</stringProp>
```

- [ ] **Step 4: Run the JMeter load test to verify it still passes**

With the app still running:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn verify -Pjmeter-load-test -pl graphql/spring-demo
```

Expected: 0 errors in the console summary and in `target/jmeter/results/*.csv`.

- [ ] **Step 5: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/performance/DemoSimulation.java \
        communication-protocols/graphql/spring-demo/src/test/jmeter/DemoSimulation.jmx
git commit -m "test(communication-protocols): update GraphQL load tests for the pagination connection shape"
```

---

### Task 7: README — pagination & filtering documentation

**Files:**
- Modify: `communication-protocols/graphql/spring-demo/README.md`
- Modify: `communication-protocols/graphql/README.md`

**Interfaces:** None — documentation only.

- [ ] **Step 1: Update the module README's opening line and full-catalog example**

In `communication-protocols/graphql/spring-demo/README.md`, replace:

```markdown
Single Spring Boot app exposing a GraphQL schema over `Product`/`Review` data, covering query + nested fetch, DataLoader batching, mutation, and subscription.
```

with:

```markdown
Single Spring Boot app exposing a GraphQL schema over `Product`/`Review` data, covering query + nested fetch, DataLoader batching, mutation, subscription, and Relay-style cursor pagination with filtering.
```

Replace:

```markdown
**Query — full catalog:**

```bash
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ products { id name priceCents } }"}'
```
```

with:

```markdown
**Query — first page of the catalog (10 by default):**

```bash
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ products { edges { node { id name priceCents } cursor } pageInfo { hasNextPage endCursor } totalCount } }"}'
```
```

Replace:

```markdown
**Query — one product with nested reviews (the DataLoader pattern):**

```bash
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ products { id name reviews { author rating comment } } }"}'
```

Watch the console: even though this fetches reviews for 40 products, `ReviewService` logs `batch fetching reviews for 40 products in one call` exactly once — the whole point of `@BatchMapping`. A naive per-product resolver would instead log (and query) once per product, 40 times.
```

with:

```markdown
**Query — one product with nested reviews (the DataLoader pattern):**

```bash
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ products(first: 40) { edges { node { id name reviews { edges { node { author rating comment } } } } } } }"}'
```

Watch the console: even though this fetches reviews for 40 products, `ReviewService` logs `batch fetching reviews for 40 products in one call` exactly once — the whole point of `@BatchMapping`. A naive per-product resolver would instead log (and query) once per product, 40 times.
```

- [ ] **Step 2: Add a new "Pagination & filtering" section**

In `communication-protocols/graphql/spring-demo/README.md`, insert a new section directly before `## Security`:

```markdown
## Pagination & filtering

`products` and `Product.reviews` both use Relay-style cursor connections (`edges`/`node`/`cursor`/`pageInfo`) instead of bare lists — `first` defaults to 10 and is clamped to 50; pagination is forward-only (`first`/`after`, no `last`/`before`).

**Page through the catalog:**

```bash
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ products(first: 5) { edges { node { name } cursor } pageInfo { hasNextPage endCursor } } }"}'

# feed the previous response'\''s pageInfo.endCursor in as "after" for the next page:
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ products(first: 5, after: \"<endCursor from previous response>\") { edges { node { name } } pageInfo { hasNextPage } } }"}'
```

**Filter products by name and price range:**

```bash
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ products(filter: { nameContains: \"pro\", minPriceCents: 1000, maxPriceCents: 3000 }, first: 10) { edges { node { name priceCents } } totalCount } }"}'
```

**Filter reviews by minimum rating:**

```bash
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ product(id: \"p1\") { reviews(filter: { minRating: 4 }) { edges { node { author rating } } } } }"}'
```

**Caveat:** a cursor encodes a position in the filtered, ordered list it was issued from — reusing a cursor from one `filter` against a different `filter` (or against no filter at all) returns whatever that position happens to be in the new list, not the same logical page.
```

- [ ] **Step 3: Update the load-test description**

In `communication-protocols/graphql/spring-demo/README.md`, replace:

```markdown
Both load tests drive the same three requests (products+reviews query, product-by-id query, addReview mutation) with the same pacing story — 2 users ramped a few seconds apart, 500ms between calls — designed to be watched in the app's logs rather than to measure throughput. Subscriptions aren't covered by either load test since they're WebSocket sessions, not request/response calls.
```

with:

```markdown
Both load tests drive the same three requests (products+reviews connection query, product-by-id query, addReview mutation) with the same pacing story — 2 users ramped a few seconds apart, 500ms between calls — designed to be watched in the app's logs rather than to measure throughput. Subscriptions aren't covered by either load test since they're WebSocket sessions, not request/response calls.
```

- [ ] **Step 4: Update the top-level module README's pattern table and add a pattern section**

In `communication-protocols/graphql/README.md`, replace:

```markdown
## The four patterns

| Pattern | Field/operation | What it demonstrates |
|---|---|---|
| Query + nested fetch | `products { reviews { ... } }` | Client asks for exactly the fields it wants, including a nested child collection, in one round trip |
| DataLoader batching | `Product.reviews` via `@BatchMapping` | Solves the N+1 problem: fetching `reviews` for N products in one query triggers **one** batched call, not N |
| Mutation | `addReview` | A write that returns the created object and publishes it to the subscription stream |
| Subscription | `reviewAdded(productId)` | Real-time push over a GraphQL-over-WebSocket session, optionally filtered server-side by `productId` |
```

with:

```markdown
## The five patterns

| Pattern | Field/operation | What it demonstrates |
|---|---|---|
| Query + nested fetch | `products { reviews { ... } }` | Client asks for exactly the fields it wants, including a nested child collection, in one round trip |
| DataLoader batching | `Product.reviews` via `@BatchMapping` | Solves the N+1 problem: fetching `reviews` for N products in one query triggers **one** batched call, not N |
| Mutation | `addReview` | A write that returns the created object and publishes it to the subscription stream |
| Subscription | `reviewAdded(productId)` | Real-time push over a GraphQL-over-WebSocket session, optionally filtered server-side by `productId` |
| Pagination & filtering | `products(filter, first, after)`, `Product.reviews(filter, first, after)` | Relay-style cursor connections (`edges`/`node`/`cursor`/`pageInfo`) plus input-object filtering, at both the root-query and nested/batch-mapped level |
```

Add a new pattern section directly after `### Subscription`'s "Typical use cases" list and before `## Running the demo`:

```markdown
### Pagination & filtering

**Pros**
- `edges`/`node`/`cursor`/`pageInfo` is the GraphQL-idiomatic pagination shape (Relay connections) — a client can page forward and know whether more data exists without a separate count call
- Input-object filters (`ProductFilter`, `ReviewFilter`) keep filtering criteria typed and self-documenting in the schema, instead of ad-hoc string query parameters
- The same generic connection wrapper works for both a root query (`products`) and a nested/batch-mapped field (`Product.reviews`)

**Cons**
- More verbose response shape than a bare list — every item is wrapped in an `edges { node { ... } }` layer
- This demo's cursors encode a plain list position, not an opaque, storage-independent key — fine for in-memory data, not representative of a cursor scheme robust to underlying reordering
- A cursor issued under one filter isn't guaranteed meaningful against a different filter — callers are expected to page within one filter, not mix cursors across filters

**Typical use cases**
- Any list endpoint large enough that returning everything in one response is wasteful (product catalogs, comment threads, activity feeds)
- APIs where clients need to narrow a large collection by one or more criteria before paging through it
```

- [ ] **Step 5: Update the "all four patterns" cross-reference**

In `communication-protocols/graphql/README.md`, replace:

```markdown
See [spring-demo/README.md](spring-demo/README.md) for `curl` and subscription walkthroughs of all four patterns.
```

with:

```markdown
See [spring-demo/README.md](spring-demo/README.md) for `curl` and subscription walkthroughs of all five patterns.
```

- [ ] **Step 6: Verify the docs render sensibly**

No automated check for markdown; read both files back and confirm the tables and code fences are well-formed, and that no stale "four patterns"/"full catalog" wording remains.

- [ ] **Step 7: Commit**

```bash
git add communication-protocols/graphql/spring-demo/README.md communication-protocols/graphql/README.md
git commit -m "docs(communication-protocols): document GraphQL pagination and filtering"
```

---

## Final verification

- [ ] Run `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn clean package -pl graphql/spring-demo -am` from `communication-protocols/` — expect BUILD SUCCESS.
- [ ] Start the app (`mvn -pl graphql/spring-demo spring-boot:run`) and manually verify the pagination/filtering `curl` examples in the new README section against a live instance, including paging past the first page with a real `endCursor`.
