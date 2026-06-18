# MongoDB Indexing & Text Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fifth MongoDB pattern — indexing and text search — to the existing demo at `noSQL/mongodb/spring-demo`, reusing the `products` collection.

**Architecture:** A new `search` package with an index-creation initializer and a search service, wired into the existing `DemoController` via two new endpoints. Mirrors every other pattern in this module: a package per pattern, `MongoTemplate` mocked directly in unit tests, REST-triggered.

**Tech Stack:** Java 21, Spring Boot 3.4.x, `spring-boot-starter-data-mongodb` (`MongoTemplate`, `IndexOperations`, `TextCriteria`).

## Global Constraints

- Spec: `docs/superpowers/specs/2026-06-18-mongodb-indexing-search-design.md` — follow it exactly.
- No new collection, no changes to the `Product` entity (`noSQL/mongodb/spring-demo/src/main/java/com/testingai/mongodb/crud/Product.java`) — text search works over the existing `name` field.
- Two indexes only: a text index on `Product.name`, and a compound index on `(price, stock)`. Created explicitly via `MongoTemplate.indexOps(Product.class).ensureIndex(...)` in a `@PostConstruct` method — not via `@Indexed`/`@TextIndexed` annotations or `auto-index-creation`.
- No `explain()` output surfaced through the application or REST layer — index usage is verified via documented `mongosh` commands in the README, not application code.
- Tests mock `MongoTemplate` directly via Mockito — the same convention used by every existing service in this module (`ProductServiceTest`, `OrderServiceTest`, `OrderAggregationServiceTest`).
- New endpoints: `GET /demo/products/search?q=<text>` and `GET /demo/products/price-range?min=&max=`, added to the existing `DemoController`.

---

### Task 1: Index creation and search service

**Files:**
- Create: `noSQL/mongodb/spring-demo/src/main/java/com/testingai/mongodb/search/SearchIndexInitializer.java`
- Create: `noSQL/mongodb/spring-demo/src/main/java/com/testingai/mongodb/search/ProductSearchService.java`
- Test: `noSQL/mongodb/spring-demo/src/test/java/com/testingai/mongodb/search/SearchIndexInitializerTest.java`
- Test: `noSQL/mongodb/spring-demo/src/test/java/com/testingai/mongodb/search/ProductSearchServiceTest.java`

**Interfaces:**
- Consumes: `Product` (`com.testingai.mongodb.crud.Product`, fields `id`, `name`, `price`, `stock`).
- Produces: `ProductSearchService.searchByText(String text): List<Product>` and `ProductSearchService.findByPriceRange(double min, double max): List<Product>`. Task 2 (`DemoController`) consumes `ProductSearchService` directly.

- [ ] **Step 1: Write the failing tests**

Create `noSQL/mongodb/spring-demo/src/test/java/com/testingai/mongodb/search/SearchIndexInitializerTest.java`:

```java
package com.testingai.mongodb.search;

import com.testingai.mongodb.crud.Product;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchIndexInitializerTest {

	@InjectMocks
	private SearchIndexInitializer initializer;

	@Mock
	private MongoTemplate mongoTemplate;

	@Mock
	private IndexOperations indexOperations;

	@Test
	void createIndexes_shouldEnsureBothIndexesWithoutThrowing() {
		when(mongoTemplate.indexOps(Product.class)).thenReturn(indexOperations);
		when(indexOperations.ensureIndex(any())).thenReturn("ok");

		assertThatCode(() -> initializer.createIndexes()).doesNotThrowAnyException();

		verify(indexOperations, times(2)).ensureIndex(any());
	}
}
```

Create `noSQL/mongodb/spring-demo/src/test/java/com/testingai/mongodb/search/ProductSearchServiceTest.java`:

```java
package com.testingai.mongodb.search;

import com.testingai.mongodb.crud.Product;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSearchServiceTest {

	@InjectMocks
	private ProductSearchService productSearchService;

	@Mock
	private MongoTemplate mongoTemplate;

	@Test
	void searchByText_shouldReturnMatchingProducts() {
		List<Product> expected = List.of(new Product("p1", "Widget", 9.99, 100));
		when(mongoTemplate.find(any(), eq(Product.class))).thenReturn(expected);

		List<Product> result = productSearchService.searchByText("Widget");

		assertThat(result).isEqualTo(expected);
	}

	@Test
	void findByPriceRange_shouldReturnProductsWithinRange() {
		List<Product> expected = List.of(new Product("p1", "Widget", 9.99, 100));
		when(mongoTemplate.find(any(Query.class), eq(Product.class))).thenReturn(expected);

		List<Product> result = productSearchService.findByPriceRange(5.0, 50.0);

		assertThat(result).isEqualTo(expected);
	}
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd noSQL/mongodb/spring-demo && mvn -q test -Dtest=SearchIndexInitializerTest,ProductSearchServiceTest`
Expected: FAIL with compilation errors — `SearchIndexInitializer` and `ProductSearchService` don't exist yet.

- [ ] **Step 3: Write the minimal implementation**

Create `noSQL/mongodb/spring-demo/src/main/java/com/testingai/mongodb/search/SearchIndexInitializer.java`:

```java
package com.testingai.mongodb.search;

import com.testingai.mongodb.crud.Product;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SearchIndexInitializer {

	private final MongoTemplate mongoTemplate;

	@PostConstruct
	public void createIndexes() {
		mongoTemplate.indexOps(Product.class).ensureIndex(TextIndexDefinition.builder().onField("name").build());
		mongoTemplate.indexOps(Product.class)
				.ensureIndex(new Index().on("price", Sort.Direction.ASC).on("stock", Sort.Direction.ASC));
		log.info("[SearchIndexInitializer] Ensured text index on products.name and compound index on products.(price, stock)");
	}
}
```

Create `noSQL/mongodb/spring-demo/src/main/java/com/testingai/mongodb/search/ProductSearchService.java`:

```java
package com.testingai.mongodb.search;

import com.testingai.mongodb.crud.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.TextQuery.queryText;

@Service
@RequiredArgsConstructor
public class ProductSearchService {

	private final MongoTemplate mongoTemplate;

	public List<Product> searchByText(String text) {
		return mongoTemplate.find(queryText(TextCriteria.forDefaultLanguage().matching(text)), Product.class);
	}

	public List<Product> findByPriceRange(double min, double max) {
		Query query = new Query(where("price").gte(min).lte(max));
		return mongoTemplate.find(query, Product.class);
	}
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd noSQL/mongodb/spring-demo && mvn -q test -Dtest=SearchIndexInitializerTest,ProductSearchServiceTest`
Expected: PASS, 3/3 tests green.

- [ ] **Step 5: Commit**

```bash
git add noSQL/mongodb/spring-demo/src/main/java/com/testingai/mongodb/search noSQL/mongodb/spring-demo/src/test/java/com/testingai/mongodb/search
git commit -m "$(cat <<'EOF'
feat(nosql): add MongoDB indexing & text search pattern

SearchIndexInitializer ensures a text index on products.name and a
compound index on (price, stock) at startup. ProductSearchService
queries through both. Indexes created explicitly via
MongoTemplate.indexOps, not annotations/auto-index-creation, matching
this module's explicit-infrastructure-setup style.
EOF
)"
```

---

### Task 2: Wire the new patterns into DemoController

**Files:**
- Modify: `noSQL/mongodb/spring-demo/src/main/java/com/testingai/mongodb/controller/DemoController.java`
- Modify: `noSQL/mongodb/spring-demo/src/test/java/com/testingai/mongodb/controller/DemoControllerTest.java`

**Interfaces:**
- Consumes: `ProductSearchService` (Task 1, `search` package) — `searchByText(String): List<Product>`, `findByPriceRange(double, double): List<Product>`.
- Produces: the REST endpoints `GET /demo/products/search?q=` and `GET /demo/products/price-range?min=&max=` that Task 3's manual verification exercises.

- [ ] **Step 1: Write the failing tests**

In `noSQL/mongodb/spring-demo/src/test/java/com/testingai/mongodb/controller/DemoControllerTest.java`, add this import alongside the existing ones:

```java
import com.testingai.mongodb.search.ProductSearchService;
```

Add this field alongside the existing `@MockitoBean` fields (`productService`, `orderService`, `aggregationService`):

```java
	@MockitoBean
	private ProductSearchService productSearchService;
```

Add these two test methods to the class, alongside the existing test methods:

```java
	@Test
	void searchProducts_shouldReturn200AndDelegate() throws Exception {
		when(productSearchService.searchByText("Widget")).thenReturn(List.of());

		mockMvc.perform(get("/demo/products/search").param("q", "Widget")).andExpect(status().isOk());

		verify(productSearchService).searchByText("Widget");
	}

	@Test
	void productsByPriceRange_shouldReturn200AndDelegate() throws Exception {
		when(productSearchService.findByPriceRange(5.0, 50.0)).thenReturn(List.of());

		mockMvc.perform(get("/demo/products/price-range").param("min", "5.0").param("max", "50.0"))
				.andExpect(status().isOk());

		verify(productSearchService).findByPriceRange(5.0, 50.0);
	}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd noSQL/mongodb/spring-demo && mvn -q test -Dtest=DemoControllerTest`
Expected: FAIL with compilation errors — `DemoController` has no `productSearchService`-backed endpoints yet, and the constructor injection won't match.

- [ ] **Step 3: Write the minimal implementation**

In `noSQL/mongodb/spring-demo/src/main/java/com/testingai/mongodb/controller/DemoController.java`, the current file is:

```java
package com.testingai.mongodb.controller;

import com.testingai.mongodb.aggregation.OrderAggregationService;
import com.testingai.mongodb.aggregation.StatusSummary;
import com.testingai.mongodb.crud.Product;
import com.testingai.mongodb.crud.ProductService;
import com.testingai.mongodb.transaction.Order;
import com.testingai.mongodb.transaction.OrderService;
import com.testingai.mongodb.transaction.PlaceOrderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

	private final ProductService productService;
	private final OrderService orderService;
	private final OrderAggregationService aggregationService;

	@PostMapping("/products")
	public Product createProduct(@RequestBody Product product) {
		return productService.create(product);
	}

	@GetMapping("/products/{id}")
	public Product getProduct(@PathVariable String id) {
		return productService.findById(id);
	}

	@PutMapping("/products/{id}")
	public Product updateProduct(@PathVariable String id, @RequestBody Product product) {
		return productService.update(id, product);
	}

	@DeleteMapping("/products/{id}")
	public void deleteProduct(@PathVariable String id) {
		productService.delete(id);
	}

	@PostMapping("/orders")
	public Order placeOrder(@RequestBody PlaceOrderRequest request) {
		return orderService.placeOrder(request.productId(), request.quantity());
	}

	@GetMapping("/aggregation")
	public List<StatusSummary> aggregation() {
		return aggregationService.summarizeByStatus();
	}
}
```

Replace it entirely with:

```java
package com.testingai.mongodb.controller;

import com.testingai.mongodb.aggregation.OrderAggregationService;
import com.testingai.mongodb.aggregation.StatusSummary;
import com.testingai.mongodb.crud.Product;
import com.testingai.mongodb.crud.ProductService;
import com.testingai.mongodb.search.ProductSearchService;
import com.testingai.mongodb.transaction.Order;
import com.testingai.mongodb.transaction.OrderService;
import com.testingai.mongodb.transaction.PlaceOrderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

	private final ProductService productService;
	private final OrderService orderService;
	private final OrderAggregationService aggregationService;
	private final ProductSearchService productSearchService;

	@PostMapping("/products")
	public Product createProduct(@RequestBody Product product) {
		return productService.create(product);
	}

	@GetMapping("/products/{id}")
	public Product getProduct(@PathVariable String id) {
		return productService.findById(id);
	}

	@PutMapping("/products/{id}")
	public Product updateProduct(@PathVariable String id, @RequestBody Product product) {
		return productService.update(id, product);
	}

	@DeleteMapping("/products/{id}")
	public void deleteProduct(@PathVariable String id) {
		productService.delete(id);
	}

	@GetMapping("/products/search")
	public List<Product> searchProducts(@RequestParam String q) {
		return productSearchService.searchByText(q);
	}

	@GetMapping("/products/price-range")
	public List<Product> productsByPriceRange(@RequestParam double min, @RequestParam double max) {
		return productSearchService.findByPriceRange(min, max);
	}

	@PostMapping("/orders")
	public Order placeOrder(@RequestBody PlaceOrderRequest request) {
		return orderService.placeOrder(request.productId(), request.quantity());
	}

	@GetMapping("/aggregation")
	public List<StatusSummary> aggregation() {
		return aggregationService.summarizeByStatus();
	}
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd noSQL/mongodb/spring-demo && mvn -q test -Dtest=DemoControllerTest`
Expected: PASS, 8/8 tests green (6 existing + 2 new).

- [ ] **Step 5: Run the full unit test suite**

Run: `cd noSQL/mongodb/spring-demo && mvn -q test`
Expected: all tests across every package pass (Tasks 1-2 combined with the pre-existing suite).

- [ ] **Step 6: Commit**

```bash
git add noSQL/mongodb/spring-demo/src/main/java/com/testingai/mongodb/controller noSQL/mongodb/spring-demo/src/test/java/com/testingai/mongodb/controller
git commit -m "$(cat <<'EOF'
feat(nosql): wire indexing & search endpoints into DemoController

GET /demo/products/search?q= and GET /demo/products/price-range?min=&max=
EOF
)"
```

---

### Task 3: README and live verification

**Files:**
- Modify: `noSQL/mongodb/README.md`

**Interfaces:**
- Consumes: the cluster (already built) and the new endpoints from Task 2 — this is where the live system gets exercised for the first time.

- [ ] **Step 1: Add curl examples to "Trigger endpoints"**

In `noSQL/mongodb/README.md`, find this exact block:

```markdown
# Aggregation — revenue and order count per status
curl "http://localhost:8084/demo/aggregation"

# Delete a product
curl -X DELETE "http://localhost:8084/demo/products/<id>"
```

Replace it with:

```markdown
# Aggregation — revenue and order count per status
curl "http://localhost:8084/demo/aggregation"

# Full-text search by product name
curl "http://localhost:8084/demo/products/search?q=Widget"

# Range query backed by the compound (price, stock) index
curl "http://localhost:8084/demo/products/price-range?min=5&max=50"

# Delete a product
curl -X DELETE "http://localhost:8084/demo/products/<id>"
```

- [ ] **Step 2: Update "Collection characteristics" with the indexes**

Find this exact table row:

```markdown
| `products` | CRUD, Transactions | `id`, `name`, `price`, `stock` |
```

Replace it with:

```markdown
| `products` | CRUD, Transactions, Indexing & Search | `id`, `name`, `price`, `stock` — text index on `name`, compound index on `(price, stock)` |
```

- [ ] **Step 3: Add the index-verification section**

Find this exact line:

```markdown
## Monitoring
```

Insert a new section immediately before it:

```markdown
## Verify the indexes are used

```bash
# Confirm the indexes exist
docker exec mongo1 mongosh ecommerce --quiet --eval "db.products.getIndexes()"

# Text search uses the text index (look for IXSCAN on the text index in the plan)
docker exec mongo1 mongosh ecommerce --quiet --eval 'db.products.find({ $text: { $search: "Widget" } }).explain("executionStats")'

# Price-range query uses the compound index (look for IXSCAN on price_1_stock_1)
docker exec mongo1 mongosh ecommerce --quiet --eval 'db.products.find({ price: { $gte: 5, $lte: 50 } }).sort({ stock: -1 }).explain("executionStats")'
```

## Monitoring
```

- [ ] **Step 4: Bring the cluster up if it isn't already, and confirm the app starts**

```bash
cd noSQL/mongodb/docker
docker compose ps
```
Expected: `mongo1`, `mongo2`, `mongo3` healthy. If not running, `docker compose up -d` and wait ~30 seconds.

```bash
cd ../spring-demo
mvn spring-boot:run > /tmp/mongo-app.log 2>&1 &
sleep 20
grep -i "Started MongoDbDemoApplication" /tmp/mongo-app.log
grep -i "SearchIndexInitializer" /tmp/mongo-app.log
```
Expected: a line containing `Started MongoDbDemoApplication`, and a line containing `[SearchIndexInitializer] Ensured text index...`.

If port 8084 is already in use by an app instance started another way (e.g. via an IDE), skip starting a new one and just confirm the existing instance is reachable: `curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8084/swagger-ui/index.html` should print `200`.

- [ ] **Step 5: Seed a product and exercise both new endpoints**

```bash
curl -s -X POST "http://localhost:8084/demo/products" -H "Content-Type: application/json" -d '{"name":"Search Test Widget","price":15.0,"stock":20}'
curl -s "http://localhost:8084/demo/products/search?q=Widget"
curl -s "http://localhost:8084/demo/products/price-range?min=5&max=50"
```
Expected: the create call returns a product JSON with an `id`; both subsequent calls return a JSON array containing that product (or any other previously-created product whose name/price falls in range).

- [ ] **Step 6: Verify the indexes are actually used**

```bash
docker exec mongo1 mongosh ecommerce --quiet --eval "db.products.getIndexes()"
```
Expected: three indexes listed — the default `_id_` index, a text index (shows up with `"name": "name_text"` or similar), and a compound index named `price_1_stock_1`.

```bash
docker exec mongo1 mongosh ecommerce --quiet --eval 'db.products.find({ price: { $gte: 5, $lte: 50 } }).sort({ stock: -1 }).explain("executionStats")' | grep -A2 '"stage"'
```
Expected: `"stage" : "IXSCAN"` appears (not `"COLLSCAN"`), confirming the compound index is used for this query.

- [ ] **Step 7: Run the full unit test suite one more time**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy/noSQL/mongodb/spring-demo
mvn -q test
```
Expected: all tests pass.

- [ ] **Step 8: Commit**

```bash
git add noSQL/mongodb/README.md
git commit -m "$(cat <<'EOF'
docs(nosql): document indexing & text search pattern

Curl examples for both new endpoints, the two new indexes noted in
Collection characteristics, and a new section with mongosh commands to
verify the text and compound indexes are actually used via explain().
EOF
)"
```

If any step in this task surfaced a problem, go back and fix the relevant file in Tasks 1-2, re-commit, and re-run verification from Step 4.
