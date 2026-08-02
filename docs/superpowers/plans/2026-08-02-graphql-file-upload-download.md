# GraphQL File Upload/Download Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a sixth pattern to the GraphQL demo (`communication-protocols/graphql/`): one optional image per `Product`, uploaded/replaced by an ADMIN and downloaded by anyone, transferred over a plain REST sidecar (`/api/products/{id}/image`) since GraphQL itself carries no binary payloads — the schema only exposes `Product.imageUrl`, a pointer to that endpoint.

**Architecture:** New `product_images` table (Postgres, one row max per product, `product_id` as its own PK/FK). `Product.imageUrl` resolves via a `@BatchMapping` method on the existing `DemoController` — one batched existence check per query, avoiding the N+1 pitfall pattern #2 (DataLoader batching) already teaches. A new `ProductImageController` (plain `@RestController`, not part of the GraphQL schema) handles the actual bytes: `POST` (ADMIN-only, validated to be `image/*`, ≤5MB) and `GET` (public). The Angular `product-detail` page shows the image and, for ADMIN users, a file-upload control that calls the REST endpoint directly via `HttpClient` — not through Apollo.

**Tech Stack:** Spring Boot 3 (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-graphql`, `spring-boot-starter-security`), Liquibase, Postgres/H2, Angular 20 + Apollo Angular + Angular Material.

## Global Constraints

- One image per product — upload replaces any previous image, no galleries.
- Upload requires `ROLE_ADMIN` (`@PreAuthorize("hasRole('ADMIN')")`); download is public (`permitAll()` at the HTTP layer).
- Image bytes stored in Postgres (`bytea`/H2 `BLOB` via Liquibase's generic `BLOB` type), not the filesystem — no new infrastructure.
- Max upload size: 5MB (`spring.servlet.multipart.max-file-size`/`max-request-size`), enforced in both `src/main/resources/application.yml` and `src/test/resources/application.yml` (the test resource fully replaces the main one on the test classpath, so settings needed during tests must be duplicated there — this repo's existing pattern, see `datasource`/`jpa`/`liquibase` blocks already duplicated in both files).
- File transfer never goes through GraphQL or Apollo — plain REST, plain `HttpClient`.
- Angular UI change is scoped to `product-detail` only — no thumbnails on `product-list`.
- No e2e tests for the Angular change — Karma/Jasmine unit tests only, matching this module's existing convention.
- Follow existing package/test conventions exactly: backend services live in `domain/`, entities in `entity/`, repositories in `repository/`; `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `WebTestClient`/`HttpGraphQlTester` for integration tests (not `MockMvc`), `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = Replace.NONE)` for repository tests.

---

## File Structure

**Backend (`communication-protocols/graphql/spring-demo/`):**

| File | Change |
|---|---|
| `src/main/resources/db/changelog/db.changelog-8-create-product-images-table.xml` | Create — new `product_images` table |
| `src/main/resources/db/changelog/db.changelog-master.xml` | Modify — include the new changelog |
| `src/main/java/com/testingai/graphql/entity/ProductImageEntity.java` | Create |
| `src/main/java/com/testingai/graphql/repository/ProductImageRepository.java` | Create |
| `src/main/java/com/testingai/graphql/domain/ProductImage.java` | Create — small record for the REST layer, not GraphQL-exposed |
| `src/main/java/com/testingai/graphql/domain/ProductImageService.java` | Create (Task 2), then extend (Task 3) |
| `src/main/resources/graphql/schema.graphqls` | Modify — add `Product.imageUrl: String` |
| `src/main/java/com/testingai/graphql/controller/DemoController.java` | Modify — add `productImageUrl` `@BatchMapping` |
| `src/main/java/com/testingai/graphql/controller/ProductImageController.java` | Create — REST upload/download |
| `src/main/java/com/testingai/graphql/config/SecurityConfig.java` | Modify — permit the download GET |
| `src/main/resources/application.yml` | Modify — multipart size limits |
| `src/test/resources/application.yml` | Modify — same multipart size limits |
| `src/test/java/com/testingai/graphql/repository/ProductImageRepositoryTest.java` | Create |
| `src/test/java/com/testingai/graphql/controller/DemoIntegrationTest.java` | Modify — `imageUrl` batch-mapping cases |
| `src/test/java/com/testingai/graphql/controller/ProductImageControllerTest.java` | Create |
| `communication-protocols/graphql/README.md` | Modify — sixth pattern |
| `communication-protocols/README.md` | Modify — one line |
| `communication-protocols/graphql/spring-demo/README.md` | Modify — curl walkthrough |

**Frontend (`communication-protocols/graphql/angular-demo/`):**

| File | Change |
|---|---|
| `proxy.conf.json` | Modify — proxy `/api` too |
| `src/app/core/graphql/graphql.models.ts` | Modify — `Product.imageUrl` |
| `src/app/features/catalog/catalog.gql.ts` | Modify — `PRODUCT_QUERY` requests `imageUrl` |
| `src/app/features/catalog/product-catalog.service.ts` | Modify — `uploadProductImage` |
| `src/app/features/catalog/product-catalog.service.spec.ts` | Modify — test for it |
| `src/app/features/catalog/product-detail.ts` | Modify — image state + upload handler |
| `src/app/features/catalog/product-detail.html` | Modify — image block |
| `src/app/features/catalog/product-detail.scss` | Modify — image styles |
| `src/app/features/catalog/product-detail.spec.ts` | Modify — new cases |
| `communication-protocols/graphql/angular-demo/README.md` | Modify — one line |

---

### Task 1: Backend data model — `product_images` table, entity, repository

**Files:**
- Create: `communication-protocols/graphql/spring-demo/src/main/resources/db/changelog/db.changelog-8-create-product-images-table.xml`
- Modify: `communication-protocols/graphql/spring-demo/src/main/resources/db/changelog/db.changelog-master.xml`
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/entity/ProductImageEntity.java`
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/repository/ProductImageRepository.java`
- Test: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/repository/ProductImageRepositoryTest.java`

**Interfaces:**
- Produces: `ProductImageEntity` (fields: `Long productId` [PK, no generation — shared with `products.id`], `String contentType`, `byte[] data`, `Instant updatedAt`), `ProductImageRepository extends JpaRepository<ProductImageEntity, Long>` with `List<Long> findProductIdsWithImage(List<Long> productIds)`.

- [ ] **Step 1: Write the failing repository test**

```java
package com.testingai.graphql.repository;

import com.testingai.graphql.entity.ProductEntity;
import com.testingai.graphql.entity.ProductImageEntity;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductImageRepositoryTest {

	@Autowired
	private ProductRepository productRepository;
	@Autowired
	private ProductImageRepository productImageRepository;

	@Test
	void findProductIdsWithImage_returnsOnlyIdsThatHaveAnImageRow() {
		Long withImageId = saveProduct("With Image");
		Long withoutImageId = saveProduct("Without Image");
		productImageRepository
				.save(new ProductImageEntity(withImageId, "image/png", new byte[] { 1, 2, 3 }, Instant.now()));

		List<Long> result = productImageRepository.findProductIdsWithImage(List.of(withImageId, withoutImageId));

		assertThat(result).containsExactly(withImageId);
	}

	@Test
	void save_replacesThePreviousImage_whenCalledTwiceForTheSameProduct() {
		Long productId = saveProduct("Replaceable");
		productImageRepository.save(new ProductImageEntity(productId, "image/png", new byte[] { 1 }, Instant.now()));

		productImageRepository
				.save(new ProductImageEntity(productId, "image/jpeg", new byte[] { 2, 2 }, Instant.now()));

		ProductImageEntity latest = productImageRepository.findById(productId).orElseThrow();
		assertThat(latest.getContentType()).isEqualTo("image/jpeg");
		assertThat(latest.getData()).isEqualTo(new byte[] { 2, 2 });
	}

	private Long saveProduct(String name) {
		ProductEntity entity = new ProductEntity();
		entity.setName(name);
		entity.setPriceCents(1000);
		entity.setStockQty(10);
		return productRepository.save(entity).getId();
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd communication-protocols/graphql/spring-demo && mvn test -Dtest=ProductImageRepositoryTest`
Expected: FAIL to compile — `ProductImageEntity`/`ProductImageRepository` don't exist yet, and the `product_images` table doesn't exist.

- [ ] **Step 3: Create the Liquibase changelog**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.8.xsd">

    <changeSet id="8-create-product-images-table" author="migration">
        <createTable tableName="product_images">
            <column name="product_id" type="BIGINT">
                <constraints primaryKey="true" nullable="false" foreignKeyName="fk_product_images_product"
                             references="products(id)"/>
            </column>
            <column name="content_type" type="VARCHAR(255)">
                <constraints nullable="false"/>
            </column>
            <column name="data" type="BYTEA">
                <constraints nullable="false"/>
            </column>
            <column name="updated_at" type="TIMESTAMP">
                <constraints nullable="false"/>
            </column>
        </createTable>
    </changeSet>

</databaseChangeLog>
```

**Amended during implementation:** Liquibase's generic `BLOB` type doesn't resolve on H2 under `MODE=PostgreSQL` (used by `src/test/resources/application.yml`) — it raises `Unknown data type: "BLOB"` at migration time. `BYTEA` (Postgres's real column type, and one H2 recognizes directly in PostgreSQL-compatibility mode) works on both.

Add the include to `db.changelog-master.xml`, right after the existing `db.changelog-7-create-order-items-table.xml` line:

```xml
    <include file="db/changelog/db.changelog-7-create-order-items-table.xml"/>
    <include file="db/changelog/db.changelog-8-create-product-images-table.xml"/>
```

- [ ] **Step 4: Create `ProductImageEntity`**

```java
package com.testingai.graphql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "product_images")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageEntity {

	@Id
	@Column(name = "product_id")
	private Long productId;

	@Column(name = "content_type", nullable = false)
	private String contentType;

	@Column(name = "data", nullable = false)
	private byte[] data;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
```

Deliberately **not** `@Lob`: Hibernate 6's `@Lob` on a `byte[]` field maps to Postgres `oid` (large-object) storage by default, not `bytea`, unless additional JDBC type configuration is set — a plain `byte[]` field maps directly to `bytea`/`varbinary`, matching Liquibase's `BLOB` column type (which itself generates `bytea` on Postgres) with no extra configuration. Fine for the 5MB cap enforced in Task 3 — no LOB streaming needed at this size.

`productId` has no `@GeneratedValue` — it's a shared primary key with `products.id`, always assigned explicitly by `ProductImageService` (Task 2/3). `JpaRepository.save()` on an entity with a non-null, non-generated `@Id` performs a Hibernate `merge` (select-then-insert-or-update), which is exactly the upsert/replace semantics this table needs.

- [ ] **Step 5: Create `ProductImageRepository`**

```java
package com.testingai.graphql.repository;

import com.testingai.graphql.entity.ProductImageEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductImageRepository extends JpaRepository<ProductImageEntity, Long> {

	@Query("select pi.productId from ProductImageEntity pi where pi.productId in :productIds")
	List<Long> findProductIdsWithImage(@Param("productIds") List<Long> productIds);
}
```

This is a lightweight projection (just `product_id`, never the `data` column) — used later for the `Product.imageUrl` batch existence check, where loading full image bytes for every product in a query would be wasteful.

- [ ] **Step 6: Run test to verify it passes**

Run: `cd communication-protocols/graphql/spring-demo && mvn test -Dtest=ProductImageRepositoryTest`
Expected: PASS (2 tests)

- [ ] **Step 7: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/main/resources/db/changelog/db.changelog-8-create-product-images-table.xml \
        communication-protocols/graphql/spring-demo/src/main/resources/db/changelog/db.changelog-master.xml \
        communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/entity/ProductImageEntity.java \
        communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/repository/ProductImageRepository.java \
        communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/repository/ProductImageRepositoryTest.java
git commit -m "feat(communication-protocols): add product_images table, entity, and repository"
```

---

### Task 2: GraphQL `Product.imageUrl` — schema field + batched resolver

**Files:**
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/ProductImage.java`
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/ProductImageService.java`
- Modify: `communication-protocols/graphql/spring-demo/src/main/resources/graphql/schema.graphqls`
- Modify: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/controller/DemoController.java`
- Test: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/DemoIntegrationTest.java`

**Interfaces:**
- Consumes: `ProductImageRepository.findProductIdsWithImage(List<Long>)` from Task 1.
- Produces: `ProductImageService.findProductIdsWithImage(List<Long> productIds): Set<Long>`, used by `DemoController.productImageUrl`. `ProductImage` record (`String contentType, byte[] data`) — not used yet in this task, but declared now since Task 3 extends `ProductImageService` with methods returning it.

- [ ] **Step 1: Write the failing GraphQL tests**

Add to `DemoIntegrationTest`, near the other `product(id)`-based tests. First add the new autowired field and imports at the top of the class:

```java
import com.testingai.graphql.entity.ProductImageEntity;
import com.testingai.graphql.repository.ProductImageRepository;
import java.time.Instant;
```

```java
	@Autowired
	private ProductImageRepository productImageRepository;
```

Then the two test methods (follow the existing retry idiom used by every other `product(id)`-based test in this file, since `product` has a real 5% simulated failure):

```java
	@Test
	void query_productImageUrl_isNull_whenNoImageUploaded() {
		for (int attempt = 0; attempt < 20; attempt++) {
			List<ResponseError> errors = new ArrayList<>();
			GraphQlTester.Traversable afterErrors = graphQlTester.document("""
					query {
					  product(id: "%s") { imageUrl }
					}
					""".formatted(productId1)).execute().errors().satisfy(errors::addAll);

			if (errors.isEmpty()) {
				afterErrors.path("product.imageUrl").valueIsNull();
				return;
			}
		}
		fail("product query kept simulating failure across 20 attempts (5% failure rate)");
	}

	@Test
	void query_productImageUrl_resolvesToRestDownloadPath_whenImageUploaded() {
		productImageRepository
				.save(new ProductImageEntity(productId1, "image/png", new byte[] { 1, 2, 3 }, Instant.now()));

		for (int attempt = 0; attempt < 20; attempt++) {
			List<ResponseError> errors = new ArrayList<>();
			GraphQlTester.Traversable afterErrors = graphQlTester.document("""
					query {
					  product(id: "%s") { imageUrl }
					}
					""".formatted(productId1)).execute().errors().satisfy(errors::addAll);

			if (errors.isEmpty()) {
				afterErrors.path("product.imageUrl").entity(String.class)
						.isEqualTo("/api/products/" + productId1 + "/image");
				return;
			}
		}
		fail("product query kept simulating failure across 20 attempts (5% failure rate)");
	}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd communication-protocols/graphql/spring-demo && mvn test -Dtest=DemoIntegrationTest`
Expected: FAIL — `imageUrl` is not a field on `Product` in the schema yet (validation error), and `ProductImageEntity`/`ProductImageRepository` won't resolve until Task 1 is in place (they are, since Task 1 is a prior task).

- [ ] **Step 3: Add `imageUrl` to the schema**

In `schema.graphqls`, change:

```graphql
type Product {
    id: ID!
    name: String!
    priceCents: Int!
    stockQty: Int!
    categories: [Category!]!
    reviews(filter: ReviewFilter, first: Int, after: String): ReviewConnection!
}
```

to:

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

- [ ] **Step 4: Create `ProductImage`**

```java
package com.testingai.graphql.domain;

public record ProductImage(String contentType, byte[] data) {
}
```

- [ ] **Step 5: Create `ProductImageService`**

```java
package com.testingai.graphql.domain;

import com.testingai.graphql.repository.ProductImageRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ProductImageService {

	private final ProductImageRepository productImageRepository;

	public ProductImageService(ProductImageRepository productImageRepository) {
		this.productImageRepository = productImageRepository;
	}

	public Set<Long> findProductIdsWithImage(List<Long> productIds) {
		return new HashSet<>(productImageRepository.findProductIdsWithImage(productIds));
	}
}
```

- [ ] **Step 6: Add the batched resolver to `DemoController`**

Add `import java.util.Set;` to the existing import block, add the field:

```java
	private final ProductImageService productImageService;
```

(placed alongside the other `private final ...Service` fields at the top of the class — `@RequiredArgsConstructor` picks it up automatically, no constructor changes needed).

Add the resolver method, near `productCategories` (same `@BatchMapping` idiom, explicit `typeName`/`field` for the same reason — the method name doesn't match the schema field name):

```java
	/**
	 * Batch mapping for {@code Product.imageUrl} — no {@code @Argument} needed, so this uses {@code @BatchMapping}
	 * directly, same idiom as {@link #productCategories}. Resolves to the REST download path for products that have
	 * an uploaded image ({@link com.testingai.graphql.controller.ProductImageController}), null otherwise — GraphQL
	 * itself never carries the image bytes.
	 */
	@BatchMapping(typeName = "Product", field = "imageUrl")
	public Map<Product, String> productImageUrl(List<Product> products) {
		List<Long> ids = products.stream().map(product -> Long.parseLong(product.id())).toList();
		Set<Long> withImage = productImageService.findProductIdsWithImage(ids);
		Map<Product, String> result = new LinkedHashMap<>();
		for (Product product : products) {
			result.put(product,
					withImage.contains(Long.parseLong(product.id())) ? "/api/products/" + product.id() + "/image"
							: null);
		}
		return result;
	}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `cd communication-protocols/graphql/spring-demo && mvn test -Dtest=DemoIntegrationTest`
Expected: PASS (all tests in the class, including the 2 new ones)

- [ ] **Step 8: Run the full test suite to check for regressions**

Run: `cd communication-protocols/graphql/spring-demo && mvn test`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/ProductImage.java \
        communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/ProductImageService.java \
        communication-protocols/graphql/spring-demo/src/main/resources/graphql/schema.graphqls \
        communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/controller/DemoController.java \
        communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/DemoIntegrationTest.java
git commit -m "feat(communication-protocols): resolve Product.imageUrl via a batched existence check"
```

---

### Task 3: REST upload/download endpoints + security + size limits

**Files:**
- Modify: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/ProductImageService.java`
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/controller/ProductImageController.java`
- Modify: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/config/SecurityConfig.java`
- Modify: `communication-protocols/graphql/spring-demo/src/main/resources/application.yml`
- Modify: `communication-protocols/graphql/spring-demo/src/test/resources/application.yml`
- Test: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/ProductImageControllerTest.java`

**Interfaces:**
- Consumes: `ProductImageService` (Task 2), `ProductImage` record (Task 2), `ProductRepository` (existing, Task 1's `ProductImageEntity`/`ProductImageRepository`).
- Produces: `POST /api/products/{id}/image` (multipart field name `file`, `ROLE_ADMIN` required) → `204`/`400`/`404`/`413`. `GET /api/products/{id}/image` (public) → `200` with image bytes and `Content-Type`, or `404`.

- [ ] **Step 1: Write the failing controller test**

```java
package com.testingai.graphql.controller;

import com.testingai.graphql.entity.ProductEntity;
import com.testingai.graphql.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductImageControllerTest {

	@LocalServerPort
	private int port;

	@Autowired
	private ProductRepository productRepository;

	private WebTestClient webTestClient;
	private Long productId;

	@BeforeEach
	void setUp() {
		webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

		ProductEntity product = new ProductEntity();
		product.setName("Image Test Product " + System.nanoTime());
		product.setPriceCents(1000);
		product.setStockQty(5);
		productId = productRepository.save(product).getId();
	}

	@Test
	void upload_thenDownload_asAdmin_roundTripsBytes() {
		byte[] bytes = { 1, 2, 3, 4 };

		webTestClient.post().uri("/api/products/{id}/image", productId)
				.header("Authorization", basicAuthHeader("admin", "adminPassword"))
				.body(BodyInserters.fromMultipartData(multipartBody(bytes, "image/png"))).exchange().expectStatus()
				.isNoContent();

		webTestClient.get().uri("/api/products/{id}/image", productId).exchange().expectStatus().isOk()
				.expectHeader().contentType(MediaType.IMAGE_PNG).expectBody(byte[].class).isEqualTo(bytes);
	}

	@Test
	void upload_isRejected_whenAuthenticatedAsUser() {
		webTestClient.post().uri("/api/products/{id}/image", productId)
				.header("Authorization", basicAuthHeader("user", "userPassword"))
				.body(BodyInserters.fromMultipartData(multipartBody(new byte[] { 1 }, "image/png"))).exchange()
				.expectStatus().isForbidden();
	}

	@Test
	void upload_isRejected_whenAnonymous() {
		webTestClient.post().uri("/api/products/{id}/image", productId)
				.body(BodyInserters.fromMultipartData(multipartBody(new byte[] { 1 }, "image/png"))).exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	void upload_isRejected_whenContentTypeIsNotAnImage() {
		webTestClient.post().uri("/api/products/{id}/image", productId)
				.header("Authorization", basicAuthHeader("admin", "adminPassword"))
				.body(BodyInserters.fromMultipartData(multipartBody(new byte[] { 1 }, "text/plain"))).exchange()
				.expectStatus().isBadRequest();
	}

	@Test
	void upload_isRejected_whenProductDoesNotExist() {
		webTestClient.post().uri("/api/products/{id}/image", 999999999L)
				.header("Authorization", basicAuthHeader("admin", "adminPassword"))
				.body(BodyInserters.fromMultipartData(multipartBody(new byte[] { 1 }, "image/png"))).exchange()
				.expectStatus().isNotFound();
	}

	@Test
	void upload_isRejected_whenFileExceedsSizeLimit() {
		byte[] tooLarge = new byte[5 * 1024 * 1024 + 1];

		webTestClient.post().uri("/api/products/{id}/image", productId)
				.header("Authorization", basicAuthHeader("admin", "adminPassword"))
				.body(BodyInserters.fromMultipartData(multipartBody(tooLarge, "image/png"))).exchange().expectStatus()
				.isEqualTo(413);
	}

	@Test
	void download_returnsNotFound_whenNoImageUploaded() {
		webTestClient.get().uri("/api/products/{id}/image", productId).exchange().expectStatus().isNotFound();
	}

	private static MultiValueMap<String, HttpEntity<?>> multipartBody(byte[] bytes, String contentType) {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.part("file", new ByteArrayResource(bytes) {
			@Override
			public String getFilename() {
				return "image.bin";
			}
		}).contentType(MediaType.parseMediaType(contentType));
		return builder.build();
	}

	private static String basicAuthHeader(String username, String password) {
		String credentials = username + ":" + password;
		return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd communication-protocols/graphql/spring-demo && mvn test -Dtest=ProductImageControllerTest`
Expected: FAIL to compile — `ProductImageController` doesn't exist yet; once it does, `upload_isRejected_whenFileExceedsSizeLimit` fails without the multipart size limit configured.

- [ ] **Step 3: Extend `ProductImageService`**

Replace the whole file with:

```java
package com.testingai.graphql.domain;

import com.testingai.graphql.entity.ProductImageEntity;
import com.testingai.graphql.repository.ProductImageRepository;
import com.testingai.graphql.repository.ProductRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ProductImageService {

	private final ProductRepository productRepository;
	private final ProductImageRepository productImageRepository;

	public ProductImageService(ProductRepository productRepository, ProductImageRepository productImageRepository) {
		this.productRepository = productRepository;
		this.productImageRepository = productImageRepository;
	}

	public Set<Long> findProductIdsWithImage(List<Long> productIds) {
		return new HashSet<>(productImageRepository.findProductIdsWithImage(productIds));
	}

	public void upload(Long productId, String contentType, byte[] data) {
		if (!productRepository.existsById(productId)) {
			throw new NoSuchElementException("No product with id " + productId);
		}
		productImageRepository.save(new ProductImageEntity(productId, contentType, data, Instant.now()));
	}

	public Optional<ProductImage> find(Long productId) {
		return productImageRepository.findById(productId)
				.map(entity -> new ProductImage(entity.getContentType(), entity.getData()));
	}
}
```

- [ ] **Step 4: Create `ProductImageController`**

```java
package com.testingai.graphql.controller;

import com.testingai.graphql.domain.ProductImageService;
import java.io.IOException;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST sidecar for {@code Product} image transfer. GraphQL has no native support for binary payloads, so the
 * schema only ever exposes {@code Product.imageUrl} — a pointer to these two plain endpoints (see
 * {@link DemoController#productImageUrl}). Deliberately outside the GraphQL controller/schema entirely.
 */
@RestController
@RequiredArgsConstructor
public class ProductImageController {

	private static final int MAX_FILE_SIZE_MB = 5;

	private final ProductImageService productImageService;

	@PostMapping("/api/products/{id}/image")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Void> upload(@PathVariable Long id, @RequestParam("file") MultipartFile file)
			throws IOException {
		String contentType = file.getContentType();
		if (contentType == null || !contentType.startsWith("image/")) {
			throw new IllegalArgumentException("Uploaded file must be an image, got content type: " + contentType);
		}
		productImageService.upload(id, contentType, file.getBytes());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/api/products/{id}/image")
	public ResponseEntity<byte[]> download(@PathVariable Long id) {
		return productImageService.find(id)
				.map(image -> ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
						.body(image.data()))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@ExceptionHandler(NoSuchElementException.class)
	public ResponseEntity<String> handleNotFound(NoSuchElementException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<String> handleTooLarge(MaxUploadSizeExceededException e) {
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
				.body("Uploaded file exceeds the " + MAX_FILE_SIZE_MB + "MB limit");
	}
}
```

- [ ] **Step 5: Permit the download GET in `SecurityConfig`**

Add `import org.springframework.http.HttpMethod;` to the import block.

Change:

```java
				.authorizeHttpRequests(
						auth -> auth.requestMatchers("/graphql", "/graphiql").permitAll().anyRequest().authenticated())
```

to:

```java
				.authorizeHttpRequests(auth -> auth.requestMatchers("/graphql", "/graphiql").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/products/*/image").permitAll().anyRequest().authenticated())
```

`POST /api/products/{id}/image` falls through to `anyRequest().authenticated()`, with the `ROLE_ADMIN` check enforced by `@PreAuthorize` on the controller method — same split `/graphql` already uses.

- [ ] **Step 6: Add multipart size limits to both `application.yml` files**

In `src/main/resources/application.yml`, add a `servlet` block as a sibling of the existing `graphql`/`datasource`/`jpa`/`liquibase` keys under `spring:`:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 5MB
  graphql:
    graphiql:
      enabled: true
    websocket:
      path: /graphql
  datasource:
    url: jdbc:postgresql://localhost:5433/graphqldemo
    username: graphql
    password: graphql
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        jdbc:
          batch_size: 500
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml
```

Make the identical addition to `src/test/resources/application.yml` (it fully replaces the main file on the test classpath, so the limit must be duplicated there for `upload_isRejected_whenFileExceedsSizeLimit` to actually trigger):

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 5MB
  graphql:
    graphiql:
      enabled: true
    websocket:
      path: /graphql
  datasource:
    url: jdbc:h2:mem:graphqldemo-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd communication-protocols/graphql/spring-demo && mvn test -Dtest=ProductImageControllerTest`
Expected: PASS (7 tests)

- [ ] **Step 8: Run the full test suite to check for regressions**

Run: `cd communication-protocols/graphql/spring-demo && mvn test`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/ProductImageService.java \
        communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/controller/ProductImageController.java \
        communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/config/SecurityConfig.java \
        communication-protocols/graphql/spring-demo/src/main/resources/application.yml \
        communication-protocols/graphql/spring-demo/src/test/resources/application.yml \
        communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/ProductImageControllerTest.java
git commit -m "feat(communication-protocols): add REST upload/download endpoints for product images"
```

---

### Task 4: Angular plumbing — proxy, models, query, service method

**Files:**
- Modify: `communication-protocols/graphql/angular-demo/proxy.conf.json`
- Modify: `communication-protocols/graphql/angular-demo/src/app/core/graphql/graphql.models.ts`
- Modify: `communication-protocols/graphql/angular-demo/src/app/features/catalog/catalog.gql.ts`
- Modify: `communication-protocols/graphql/angular-demo/src/app/features/catalog/product-catalog.service.ts`
- Test: `communication-protocols/graphql/angular-demo/src/app/features/catalog/product-catalog.service.spec.ts`

**Interfaces:**
- Produces: `Product.imageUrl?: string | null` on the `Product` TS interface. `ProductCatalogService.uploadProductImage(id: string, file: File): Observable<void>`, consumed by Task 5's `product-detail.ts`.

- [ ] **Step 1: Write the failing service test**

Replace `product-catalog.service.spec.ts` with (adds `HttpClient` test setup and one new `it` block; the five existing `it` blocks are unchanged):

```typescript
import { TestBed } from '@angular/core/testing';
import { Apollo } from 'apollo-angular';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { ProductCatalogService } from './product-catalog.service';
import { Connection, Product, Review, emptyConnection } from '../../core/graphql/graphql.models';

describe('ProductCatalogService', () => {
  let apollo: jasmine.SpyObj<Apollo>;
  let service: ProductCatalogService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    apollo = jasmine.createSpyObj<Apollo>(['watchQuery', 'mutate']);
    TestBed.configureTestingModule({
      providers: [{ provide: Apollo, useValue: apollo }, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProductCatalogService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('listProducts maps the products connection', (done) => {
    const connection: Connection<Product> = { ...emptyConnection<Product>(), totalCount: 1 };
    apollo.watchQuery.and.returnValue({ valueChanges: of({ data: { products: connection } }) } as never);

    service.listProducts(null, 20, null).subscribe((result) => {
      expect(result).toEqual(connection);
      done();
    });
  });

  it('getProduct maps a single product', (done) => {
    const product: Product = { id: '1', name: 'Widget', priceCents: 500, stockQty: 10 };
    apollo.watchQuery.and.returnValue({ valueChanges: of({ data: { product } }) } as never);

    service.getProduct('1').subscribe((result) => {
      expect(result).toEqual(product);
      done();
    });
  });

  it('listReviews maps the nested reviews connection, defaulting to empty when the product is missing', (done) => {
    apollo.watchQuery.and.returnValue({ valueChanges: of({ data: { product: null } }) } as never);

    service.listReviews('1', null, 20, null).subscribe((result) => {
      expect(result).toEqual(emptyConnection<Review>());
      done();
    });
  });

  it('addReview maps the created review', (done) => {
    const review: Review = { id: '9', productId: '1', author: { id: '1', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' }, rating: 5, comment: 'Great' };
    apollo.mutate.and.returnValue(of({ data: { addReview: review } }) as never);

    service.addReview({ productId: '1', rating: 5, comment: 'Great' }).subscribe((result) => {
      expect(result).toEqual(review);
      done();
    });
  });

  it('deleteReview maps the boolean result', (done) => {
    apollo.mutate.and.returnValue(of({ data: { deleteReview: true } }) as never);

    service.deleteReview('9').subscribe((result) => {
      expect(result).toBe(true);
      done();
    });
  });

  it('uploadProductImage posts multipart form data to the REST endpoint', (done) => {
    const file = new File(['abc'], 'image.png', { type: 'image/png' });

    service.uploadProductImage('1', file).subscribe(() => done());

    const req = httpMock.expectOne('/api/products/1/image');
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBe(true);
    req.flush(null);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd communication-protocols/graphql/angular-demo && npm test -- --watch=false`
Expected: FAIL — `uploadProductImage` doesn't exist on `ProductCatalogService`.

- [ ] **Step 3: Proxy `/api` in `proxy.conf.json`**

```json
{
  "/graphql": {
    "target": "http://localhost:8092",
    "secure": false,
    "ws": true,
    "changeOrigin": true
  },
  "/api": {
    "target": "http://localhost:8092",
    "secure": false,
    "changeOrigin": true
  }
}
```

- [ ] **Step 4: Add `imageUrl` to the `Product` model**

In `graphql.models.ts`, change:

```typescript
export interface Product {
  id: ID;
  name: string;
  priceCents: number;
  stockQty: number;
  categories?: Category[];
}
```

to:

```typescript
export interface Product {
  id: ID;
  name: string;
  priceCents: number;
  stockQty: number;
  categories?: Category[];
  imageUrl?: string | null;
}
```

- [ ] **Step 5: Request `imageUrl` in `PRODUCT_QUERY`**

In `catalog.gql.ts`, change:

```typescript
export const PRODUCT_QUERY = gql`
  query Product($id: ID!) {
    product(id: $id) {
      id
      name
      priceCents
      stockQty
      categories {
        id
        name
      }
    }
  }
`;
```

to:

```typescript
export const PRODUCT_QUERY = gql`
  query Product($id: ID!) {
    product(id: $id) {
      id
      name
      priceCents
      stockQty
      imageUrl
      categories {
        id
        name
      }
    }
  }
`;
```

(`PRODUCTS_QUERY`, the list query, is left unchanged — no thumbnails in scope.)

- [ ] **Step 6: Add `uploadProductImage` to `ProductCatalogService`**

Add imports at the top of `product-catalog.service.ts`:

```typescript
import { HttpClient } from '@angular/common/http';
```

Add the injected client alongside the existing `apollo` field:

```typescript
  private readonly http = inject(HttpClient);
```

Add the method (e.g. after `deleteReview`):

```typescript
  uploadProductImage(id: string, file: File): Observable<void> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<void>(`/api/products/${id}/image`, formData);
  }
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd communication-protocols/graphql/angular-demo && npm test -- --watch=false`
Expected: PASS (all `ProductCatalogService` tests, including the new one)

- [ ] **Step 8: Commit**

```bash
git add communication-protocols/graphql/angular-demo/proxy.conf.json \
        communication-protocols/graphql/angular-demo/src/app/core/graphql/graphql.models.ts \
        communication-protocols/graphql/angular-demo/src/app/features/catalog/catalog.gql.ts \
        communication-protocols/graphql/angular-demo/src/app/features/catalog/product-catalog.service.ts \
        communication-protocols/graphql/angular-demo/src/app/features/catalog/product-catalog.service.spec.ts
git commit -m "feat(communication-protocols): wire up product image upload plumbing in the Angular client"
```

---

### Task 5: Angular UI — image display and admin upload control on `product-detail`

**Files:**
- Modify: `communication-protocols/graphql/angular-demo/src/app/features/catalog/product-detail.ts`
- Modify: `communication-protocols/graphql/angular-demo/src/app/features/catalog/product-detail.html`
- Modify: `communication-protocols/graphql/angular-demo/src/app/features/catalog/product-detail.scss`
- Test: `communication-protocols/graphql/angular-demo/src/app/features/catalog/product-detail.spec.ts`

**Interfaces:**
- Consumes: `ProductCatalogService.uploadProductImage(id: string, file: File): Observable<void>` (Task 4), `Product.imageUrl` (Task 4).
- Produces: `ProductDetail.imageUrl: Signal<string | null>` (computed, cache-busted after upload), `ProductDetail.onImageSelected(event: Event): void`.

- [ ] **Step 1: Write the failing component tests**

Replace `product-detail.spec.ts` with (adds `uploadProductImage` to the spy list, an `imageUrl: null` default on the fixture product, and three new `it` blocks; the three existing `it` blocks are unchanged):

```typescript
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { ProductDetail } from './product-detail';
import { ProductCatalogService } from './product-catalog.service';
import { AuthService } from '../../core/auth/auth.service';
import { Connection, Product, Review, emptyConnection } from '../../core/graphql/graphql.models';

describe('ProductDetail', () => {
  let catalog: jasmine.SpyObj<ProductCatalogService>;
  let authService: AuthService;

  const product: Product = { id: '1', name: 'Widget', priceCents: 500, stockQty: 10, categories: [], imageUrl: null };
  const reviewsPage: Connection<Review> = {
    edges: [
      {
        cursor: 'r1',
        node: { id: '9', productId: '1', rating: 5, comment: 'Great', author: { id: '2', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' } },
      },
    ],
    pageInfo: { hasNextPage: false, endCursor: 'r1' },
    totalCount: 1,
  };

  beforeEach(() => {
    sessionStorage.clear();
    catalog = jasmine.createSpyObj<ProductCatalogService>(['getProduct', 'listReviews', 'deleteReview', 'uploadProductImage']);
    catalog.getProduct.and.returnValue(of(product));
    catalog.listReviews.and.returnValue(of(reviewsPage));
    TestBed.configureTestingModule({
      imports: [ProductDetail],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: ProductCatalogService, useValue: catalog },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '1' } } } },
      ],
    });
    authService = TestBed.inject(AuthService);
  });

  it('loads the product and its reviews for the route id', () => {
    const fixture = TestBed.createComponent(ProductDetail);
    fixture.detectChanges();

    expect(catalog.getProduct).toHaveBeenCalledWith('1');
    expect(catalog.listReviews).toHaveBeenCalledWith('1', null, 20, null);
    expect(fixture.componentInstance.product()).toEqual(product);
    expect(fixture.componentInstance.reviewEdges().length).toBe(1);
  });

  it('hides the delete-review action for a non-admin user', () => {
    authService.setSession({ username: 'user', password: 'userPassword' }, { id: '2', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' });

    const fixture = TestBed.createComponent(ProductDetail);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="delete-review-9"]')).toBeNull();
  });

  it('shows and wires the delete-review action for an admin user', () => {
    authService.setSession({ username: 'admin', password: 'adminPassword' }, { id: '3', username: 'admin', displayName: 'Demo Admin', role: 'ADMIN' });
    catalog.deleteReview.and.returnValue(of(true));

    const fixture = TestBed.createComponent(ProductDetail);
    fixture.detectChanges();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="delete-review-9"]');
    button.click();

    expect(catalog.deleteReview).toHaveBeenCalledWith('9');
  });

  it('shows a placeholder, not an img tag, when the product has no image', () => {
    const fixture = TestBed.createComponent(ProductDetail);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('img.product-image')).toBeNull();
    expect(fixture.nativeElement.querySelector('.product-image-placeholder')).not.toBeNull();
  });

  it('shows an img tag with the product imageUrl when present', () => {
    catalog.getProduct.and.returnValue(of({ ...product, imageUrl: '/api/products/1/image' }));

    const fixture = TestBed.createComponent(ProductDetail);
    fixture.detectChanges();

    const img: HTMLImageElement = fixture.nativeElement.querySelector('img.product-image');
    expect(img.src).toContain('/api/products/1/image');
  });

  it('hides the upload control for a non-admin user', () => {
    authService.setSession({ username: 'user', password: 'userPassword' }, { id: '2', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' });

    const fixture = TestBed.createComponent(ProductDetail);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="product-image-input"]')).toBeNull();
  });

  it('shows the upload control for an admin user and uploads the selected file', () => {
    authService.setSession({ username: 'admin', password: 'adminPassword' }, { id: '3', username: 'admin', displayName: 'Demo Admin', role: 'ADMIN' });
    catalog.uploadProductImage.and.returnValue(of(undefined));

    const fixture = TestBed.createComponent(ProductDetail);
    fixture.detectChanges();

    const file = new File(['abc'], 'photo.png', { type: 'image/png' });
    const input: HTMLInputElement = fixture.nativeElement.querySelector('[data-testid="product-image-input"]');
    expect(input).not.toBeNull();
    const dataTransfer = new DataTransfer();
    dataTransfer.items.add(file);
    input.files = dataTransfer.files;
    input.dispatchEvent(new Event('change'));

    expect(catalog.uploadProductImage).toHaveBeenCalledWith('1', file);
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd communication-protocols/graphql/angular-demo && npm test -- --watch=false`
Expected: FAIL — no `.product-image`/`.product-image-placeholder`/`[data-testid="product-image-input"]` elements exist yet, `uploadProductImage` is unused.

- [ ] **Step 3: Add image state and the upload handler to `ProductDetail`**

Add imports at the top of `product-detail.ts`:

```typescript
import { Component, computed, inject, signal } from '@angular/core';
```

(replaces the existing `import { Component, inject, signal } from '@angular/core';` — adds `computed`.)

Add, inside the class body (e.g. after the `reviewTotalCount` signal declaration):

```typescript
  private readonly imageCacheBuster = signal(0);
  readonly imageUrl = computed(() => {
    const url = this.product()?.imageUrl;
    if (!url) {
      return null;
    }
    return this.imageCacheBuster() ? `${url}?v=${this.imageCacheBuster()}` : url;
  });
```

Add the upload handler method (e.g. after `deleteReview`):

```typescript
  onImageSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.catalog.uploadProductImage(this.productId, file).subscribe(() => {
      this.imageCacheBuster.set(Date.now());
      input.value = '';
    });
  }
```

- [ ] **Step 4: Add the image block to `product-detail.html`**

Change:

```html
@if (product(); as p) {
  <header class="product-header">
    <div class="product-header-main">
```

to:

```html
@if (product(); as p) {
  <header class="product-header">
    <div class="product-image-block">
      @if (imageUrl(); as src) {
        <img class="product-image" [src]="src" [alt]="p.name" />
      } @else {
        <div class="product-image product-image-placeholder" aria-hidden="true">
          <mat-icon>image</mat-icon>
        </div>
      }
      @if (authService.currentUser()?.role === 'ADMIN') {
        <label class="product-image-upload">
          <input type="file" accept="image/*" (change)="onImageSelected($event)" data-testid="product-image-input" />
          Upload image
        </label>
      }
    </div>
    <div class="product-header-main">
```

(the rest of the template — `product-header-main` and `product-header-stats` — is unchanged.)

- [ ] **Step 5: Add image styles to `product-detail.scss`**

Add (e.g. after the `.product-header-stats` block):

```scss
.product-image-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.product-image {
  width: 96px;
  height: 96px;
  border-radius: var(--app-radius);
  object-fit: cover;
  background: var(--mat-sys-surface-container-highest);
}

.product-image-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--mat-sys-outline);
}

.product-image-upload {
  font-size: 0.7rem;
  color: var(--mat-sys-primary);
  cursor: pointer;

  input[type='file'] {
    display: none;
  }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd communication-protocols/graphql/angular-demo && npm test -- --watch=false`
Expected: PASS (all `ProductDetail` tests, including the 3 new ones)

- [ ] **Step 7: Run the full frontend test suite to check for regressions**

Run: `cd communication-protocols/graphql/angular-demo && npm test -- --watch=false`
Expected: PASS

- [ ] **Step 8: Manual check in the browser**

With `spring-demo` and `angular-demo` both running (per their READMEs): log in as `admin`, open a product detail page, use the new file input to upload a small PNG, confirm the image renders immediately after upload (cache-busted) and persists on page reload; log in as `user` and confirm the upload control is absent but the image (if any) still renders.

- [ ] **Step 9: Commit**

```bash
git add communication-protocols/graphql/angular-demo/src/app/features/catalog/product-detail.ts \
        communication-protocols/graphql/angular-demo/src/app/features/catalog/product-detail.html \
        communication-protocols/graphql/angular-demo/src/app/features/catalog/product-detail.scss \
        communication-protocols/graphql/angular-demo/src/app/features/catalog/product-detail.spec.ts
git commit -m "feat(communication-protocols): show and upload product images on the product detail page"
```

---

### Task 6: Documentation — sixth pattern write-up

**Files:**
- Modify: `communication-protocols/graphql/README.md`
- Modify: `communication-protocols/README.md`
- Modify: `communication-protocols/graphql/spring-demo/README.md`
- Modify: `communication-protocols/graphql/angular-demo/README.md`

**Interfaces:** None — documentation only.

- [ ] **Step 1: Add the sixth pattern to `communication-protocols/graphql/README.md`**

Change the intro paragraph (the one starting "Beyond the five patterns below") to "Beyond the six patterns below". Change the "## The five patterns" heading to "## The six patterns", and add a row to the table:

```markdown
| File upload/download | `Product.imageUrl` + REST sidecar (`POST`/`GET /api/products/{id}/image`) | GraphQL carries no binary payloads — the schema exposes only a pointer field, and the bytes move over a plain REST endpoint next to `/graphql` |
```

Add a new subsection after "### Pagination & filtering":

```markdown
### File upload/download

**Pros**
- Keeps large binary payloads off the GraphQL execution engine entirely — no query-cost or streaming complications for the schema to deal with
- The REST endpoint can be fronted by ordinary HTTP caching/CDN infrastructure, unlike a POST-only GraphQL response
- `Product.imageUrl` still fits normal field-selection ergonomics: a client that doesn't ask for it never gets an extra round trip, and the field is resolved batched (no N+1) exactly like `Product.categories`

**Cons**
- Two request lifecycles for one logical resource (schema pointer + REST fetch) instead of one
- Authorization has to be enforced twice, independently — once for the GraphQL field (implicitly, via whatever gates a `Product` query) and once for the REST endpoint (`@PreAuthorize` on `ProductImageController`) — nothing ties them together automatically
- Not part of the GraphQL spec at all, so this pattern (unlike the other five) has no schema-level standardization; every API does it slightly differently

**Typical use cases**
- Any binary attachment on a GraphQL-modeled entity: avatars, product images, PDF exports, generated reports
- APIs that want to keep binary transfer cacheable/CDN-friendly while still describing the rest of the domain in GraphQL
```

Update the `## Scope` section to add:

```markdown
File transfer is intentionally a REST sidecar (`Product.imageUrl` + `/api/products/{id}/image`), not the `graphql-multipart-request-spec` extension — GraphQL has no native binary support, and this keeps the schema's "single endpoint for everything" story honest about where it does and doesn't apply.
```

- [ ] **Step 2: Update `communication-protocols/README.md`**

Change the GraphQL row's "Demo" cell from:

```
Single Spring Boot app covering query/nested-fetch, DataLoader batching, mutation, and subscription patterns
```

to:

```
Single Spring Boot app covering query/nested-fetch, DataLoader batching, mutation, subscription, and pagination patterns, plus REST-sidecar image upload/download
```

- [ ] **Step 3: Add a curl walkthrough to `spring-demo/README.md`**

Add a new subsection after "## Pagination & filtering" and before "## Security":

```markdown
## File upload/download

`Product.imageUrl` (nullable) points at a REST endpoint, not a GraphQL field — GraphQL has no way to carry binary payloads, so the schema only ever exposes the pointer:

```bash
# upload (ADMIN only) — replace <path-to-image> with a real image file
curl -s -u admin:adminPassword -F "file=@<path-to-image>;type=image/png" \
  http://localhost:8092/api/products/1/image
# 204 No Content on success

# the schema field now resolves to the download path:
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ product(id: \"1\") { imageUrl } }"}'
# {"data":{"product":{"imageUrl":"/api/products/1/image"}}}

# download (public, no auth needed)
curl -s http://localhost:8092/api/products/1/image -o downloaded-image.png
```

Uploading again for the same product replaces the previous image (one image per product, no gallery). Uploads are capped at 5MB and must have an `image/*` content type; both are rejected with `400`/`413` respectively, and uploading to an unknown product id returns `404`.
```

- [ ] **Step 4: Add a one-line note to `angular-demo/README.md`**

In the "Feature tour" section, add a row to the table:

```markdown
| `/catalog/:id` | ...plus `imageUrl` display and (ADMIN) upload via a plain REST call — not Apollo, since GraphQL carries no binary payloads |
```

(Adjust the existing `/catalog/:id` row's pattern description in place rather than adding a duplicate row, since that route already appears once in the table.)

- [ ] **Step 5: Proofread all four files for consistency**

Re-read each changed section to confirm it reads naturally alongside the surrounding unchanged text, and that "five patterns"/"six patterns" phrasing is consistent everywhere it appears (also check `CLAUDE.md`'s module description in the repository layout table, which currently says "covering query/nested-fetch, DataLoader batching, mutation, subscription, cursor-pagination, and an e-commerce domain" for `graphql/spring-demo` — leave as-is unless it explicitly enumerates "five patterns", since it's a file listing, not a patterns list).

- [ ] **Step 6: Commit**

```bash
git add communication-protocols/graphql/README.md \
        communication-protocols/README.md \
        communication-protocols/graphql/spring-demo/README.md \
        communication-protocols/graphql/angular-demo/README.md
git commit -m "docs(communication-protocols): document the sixth GraphQL pattern (file upload/download)"
```
