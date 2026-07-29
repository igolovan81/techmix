# GraphQL Communication Protocol Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new `communication-protocols/graphql/spring-demo` module — a single Spring Boot app demonstrating GraphQL query, DataLoader-batched nested fetch, mutation, and subscription patterns against a Products↔Reviews domain.

**Architecture:** Schema-first Spring GraphQL (`spring-boot-starter-graphql`) app. `DemoController` hosts every operation (`@QueryMapping`/`@BatchMapping`/`@MutationMapping`/`@SubscriptionMapping`). `ProductCatalogService` (reuses the gRPC demo's 40-product generator) and `ReviewService` (in-memory reviews + a `Sinks.Many` event stream) are the two domain services. `DemoExceptionResolver` classifies thrown exceptions into typed GraphQL errors. `FailureSimulator` gives the `product` query a 5% simulated failure to demonstrate GraphQL's partial-failure response shape.

**Tech Stack:** Java 21, Spring Boot 3.4.4, Spring for GraphQL 1.3.x (managed by the Boot BOM), Lombok, JUnit 5, Mockito, Reactor `StepVerifier`, Gatling, JMeter.

## Global Constraints

- Module lives at `communication-protocols/graphql/spring-demo/`, registered as a new `<module>` in `communication-protocols/pom.xml` (parent version `1.0.0`, already declares Java 21, Lombok, Gatling/JMeter plugin versions — reuse those properties, do not redeclare).
- `artifactId`: `graphql-spring-demo`; base package: `com.testingai.graphql`.
- Port `8092` (server.port) — confirmed free (existing ports in this repo: 8080 default, 8081 SQS, 8082 ASB, 8083 Pulsar, 8090 kafka-ui, 8091 grpc-client, 8094/8095 reactive-programming, 9090/9091 grpc-server).
- `FailureSimulator`: `FAILURE_RATE = 0.05` constant, `maybeThrow(String context)` static method throwing `RuntimeException` — no `shouldFail(): boolean` method (per `.claude/rules/code-review.md`).
- All fields assigned once must be `private final` (per `.claude/rules/code-review.md`), except lifecycle-assigned fields (not needed in this module — no `AutoCloseable` processors here).
- No `.toString()` calls on values passed to SLF4J `{}` placeholders.
- Prefer Java 21 idioms (records, pattern matching, text blocks, `SequencedCollection`) where natural — this module's domain types are records throughout.
- `mvn test` must never require a running server (Gatling excluded via inherited surefire config; JMeter behind the `jmeter-load-test` profile, never bound to the default build).
- Every `.java` file must pass `mvn spotless:apply` (run from `communication-protocols/`) before the final commit — the repo's pre-commit hook enforces this on staged files under `communication-protocols/`.

---

### Task 1: Module scaffolding

**Files:**
- Modify: `communication-protocols/pom.xml` (add `<module>graphql/spring-demo</module>`)
- Create: `communication-protocols/graphql/spring-demo/pom.xml`
- Create: `communication-protocols/graphql/spring-demo/src/main/resources/application.yml`
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/GraphQlSpringDemoApplication.java`
- Test: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/GraphQlSpringDemoApplicationTest.java`

**Interfaces:**
- Produces: a buildable, runnable Spring Boot app on port `8092`. Later tasks add beans into this context.

- [ ] **Step 1: Register the module in the parent POM**

In `communication-protocols/pom.xml`, change:

```xml
    <modules>
        <module>grpc/server-demo</module>
        <module>grpc/client-demo</module>
    </modules>
```

to:

```xml
    <modules>
        <module>grpc/server-demo</module>
        <module>grpc/client-demo</module>
        <module>graphql/spring-demo</module>
    </modules>
```

- [ ] **Step 2: Create the module POM**

`communication-protocols/graphql/spring-demo/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.testingai</groupId>
        <artifactId>communication-protocols</artifactId>
        <version>1.0.0</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>graphql-spring-demo</artifactId>
    <name>GraphQL Spring Demo</name>
    <description>Single Spring Boot app demonstrating GraphQL query, mutation, subscription, and DataLoader-batching patterns</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-graphql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.graphql</groupId>
            <artifactId>spring-graphql-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-webflux</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.gatling.highcharts</groupId>
            <artifactId>gatling-charts-highcharts</artifactId>
            <version>${gatling.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.testingai.graphql.GraphQlSpringDemoApplication</mainClass>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>io.gatling</groupId>
                <artifactId>gatling-maven-plugin</artifactId>
                <configuration>
                    <simulationClass>com.testingai.graphql.performance.DemoSimulation</simulationClass>
                </configuration>
            </plugin>
        </plugins>
    </build>

    <profiles>
        <profile>
            <id>jmeter-load-test</id>
            <build>
                <plugins>
                    <plugin>
                        <groupId>com.lazerycode.jmeter</groupId>
                        <artifactId>jmeter-maven-plugin</artifactId>
                        <version>${jmeter-maven-plugin.version}</version>
                        <executions>
                            <execution>
                                <id>configuration</id>
                                <goals>
                                    <goal>configure</goal>
                                </goals>
                            </execution>
                            <execution>
                                <id>jmeter-tests</id>
                                <goals>
                                    <goal>jmeter</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
</project>
```

Note: `spring-webflux` (test scope) is needed only for `TomcatWebSocketClient`/`WebSocketClient`, the reactive WebSocket client classes `WebSocketGraphQlTester` needs to open a client-side connection in tests — the app itself stays a plain Servlet (Spring MVC) app via `spring-boot-starter-web`.

- [ ] **Step 3: Create `application.yml`**

`communication-protocols/graphql/spring-demo/src/main/resources/application.yml`:

```yaml
server:
  port: 8092

spring:
  graphql:
    graphiql:
      enabled: true
    websocket:
      path: /graphql
```

- [ ] **Step 4: Create the application class**

`communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/GraphQlSpringDemoApplication.java`:

```java
package com.testingai.graphql;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GraphQlSpringDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(GraphQlSpringDemoApplication.class, args);
	}
}
```

- [ ] **Step 5: Write the context-loads test**

`communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/GraphQlSpringDemoApplicationTest.java`:

```java
package com.testingai.graphql;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class GraphQlSpringDemoApplicationTest {

	@Test
	void contextLoads() {
	}
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `mvn test -pl graphql/spring-demo -am` from `communication-protocols/`
Expected: `BUILD SUCCESS`, 1 test run (context loads — no beans defined yet besides the empty `@SpringBootApplication`).

- [ ] **Step 7: Commit**

```bash
git add communication-protocols/pom.xml communication-protocols/graphql/spring-demo
git commit -m "feat(communication-protocols): scaffold graphql-spring-demo module"
```

---

### Task 2: FailureSimulator util

**Files:**
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/util/FailureSimulator.java`
- Test: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/util/FailureSimulatorTest.java`

**Interfaces:**
- Produces: `FailureSimulator.maybeThrow(String context)` — static method, throws `RuntimeException` ~5% of calls, no return value.

- [ ] **Step 1: Write the failing test**

```java
package com.testingai.graphql.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailureSimulatorTest {

	@Test
	void maybeThrow_doesNotThrowMostOfTheTime() {
		int failures = 0;
		for (int i = 0; i < 1000; i++) {
			try {
				FailureSimulator.maybeThrow("test");
			} catch (RuntimeException e) {
				failures++;
			}
		}
		// With 5% failure rate, expect roughly 50 failures; accept 5-200 range
		assertThat(failures).isBetween(5, 200);
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl graphql/spring-demo -am -Dtest=FailureSimulatorTest`
Expected: FAIL — `FailureSimulator` does not exist (compile error).

- [ ] **Step 3: Write the implementation**

```java
package com.testingai.graphql.util;

public class FailureSimulator {

	private static final double FAILURE_RATE = 0.05;

	private FailureSimulator() {
	}

	public static void maybeThrow(String context) {
		if (Math.random() < FAILURE_RATE) {
			throw new RuntimeException("Simulated 5% failure in " + context);
		}
	}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl graphql/spring-demo -am -Dtest=FailureSimulatorTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/util communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/util
git commit -m "feat(communication-protocols): add FailureSimulator to graphql-spring-demo"
```

---

### Task 3: Domain records + ProductCatalogService

**Files:**
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/Product.java`
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/Review.java`
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/AddReviewInput.java`
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/ProductCatalogService.java`
- Test: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/domain/ProductCatalogServiceTest.java`

**Interfaces:**
- Produces: `record Product(String id, String name, long priceCents)`; `record Review(String id, String productId, String author, int rating, String comment)`; `record AddReviewInput(String productId, String author, int rating, String comment)`; `ProductCatalogService.listProducts(): List<Product>`, `ProductCatalogService.findProduct(String id): Optional<Product>` — 40 products, ids `"p1"`..`"p40"`, `p1` = `"Mini Widget"`.

- [ ] **Step 1: Write the failing test**

```java
package com.testingai.graphql.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCatalogServiceTest {

	private final ProductCatalogService service = new ProductCatalogService();

	@Test
	void listProducts_returnsAllFortyProducts() {
		assertThat(service.listProducts()).hasSize(40);
	}

	@Test
	void findProduct_returnsProduct_whenKnown() {
		assertThat(service.findProduct("p1")).isPresent().get().extracting(Product::name).isEqualTo("Mini Widget");
	}

	@Test
	void findProduct_returnsEmpty_whenUnknown() {
		assertThat(service.findProduct("unknown")).isEmpty();
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl graphql/spring-demo -am -Dtest=ProductCatalogServiceTest`
Expected: FAIL — `ProductCatalogService`/`Product` do not exist.

- [ ] **Step 3: Write the domain records**

`Product.java`:

```java
package com.testingai.graphql.domain;

public record Product(String id, String name, long priceCents) {
}
```

`Review.java`:

```java
package com.testingai.graphql.domain;

public record Review(String id, String productId, String author, int rating, String comment) {
}
```

`AddReviewInput.java`:

```java
package com.testingai.graphql.domain;

public record AddReviewInput(String productId, String author, int rating, String comment) {
}
```

- [ ] **Step 4: Write `ProductCatalogService`**

```java
package com.testingai.graphql.domain;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ProductCatalogService {

	private static final List<String> PRODUCT_NAMES = List.of("Widget", "Gadget", "Gizmo", "Doohickey", "Thingamajig",
			"Contraption", "Doodad", "Whatsit", "Gizmotron", "Thingamabob");
	private static final List<String> PRODUCT_VARIANTS = List.of("Mini", "Standard", "Pro", "Max");

	private final List<Product> products = buildCatalog();

	private static List<Product> buildCatalog() {
		List<Product> catalog = new ArrayList<>();
		int id = 1;
		for (String variant : PRODUCT_VARIANTS) {
			for (String name : PRODUCT_NAMES) {
				long priceCents = 499 + (id * 137L % 4500);
				catalog.add(new Product("p" + id, variant + " " + name, priceCents));
				id++;
			}
		}
		return List.copyOf(catalog);
	}

	public Optional<Product> findProduct(String productId) {
		return products.stream().filter(product -> product.id().equals(productId)).findFirst();
	}

	public List<Product> listProducts() {
		return products;
	}
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -pl graphql/spring-demo -am -Dtest=ProductCatalogServiceTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/domain
git commit -m "feat(communication-protocols): add Product domain and catalog service to graphql-spring-demo"
```

---

### Task 4: ReviewService (seed data, batch fetch, subscription sink)

**Files:**
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/ReviewService.java`
- Test: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/domain/ReviewServiceTest.java`

**Interfaces:**
- Consumes: `ProductCatalogService.listProducts(): List<Product>` (Task 3).
- Produces: `ReviewService.findByProductIds(List<String> productIds): Map<String, List<Review>>`; `ReviewService.addReview(String productId, String author, int rating, String comment): Review`; `ReviewService.reviewAdded(): Flux<Review>`; `ReviewService.getBatchCallCount(): int` (test-observability hook proving the batch-vs-N+1 distinction).

- [ ] **Step 1: Write the failing test**

```java
package com.testingai.graphql.domain;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewServiceTest {

	private final ProductCatalogService productCatalogService = new ProductCatalogService();
	private final ReviewService service = new ReviewService(productCatalogService);

	@Test
	void findByProductIds_batchesInOneCall() {
		Map<String, List<Review>> result = service.findByProductIds(List.of("p1", "p2", "p3"));

		assertThat(result).containsOnlyKeys("p1", "p2", "p3");
		assertThat(service.getBatchCallCount()).isEqualTo(1);
	}

	@Test
	void findByProductIds_returnsEmptyList_forProductWithNoSeededReviews() {
		Map<String, List<Review>> result = service.findByProductIds(List.of("p3"));

		assertThat(result.get("p3")).isEmpty();
	}

	@Test
	void addReview_storesReview_andEmitsToSink() {
		StepVerifier.create(service.reviewAdded())
				.then(() -> service.addReview("p1", "Jordan", 5, "Great product"))
				.assertNext(review -> {
					assertThat(review.author()).isEqualTo("Jordan");
					assertThat(review.productId()).isEqualTo("p1");
				})
				.thenCancel()
				.verify();

		assertThat(service.findByProductIds(List.of("p1")).get("p1")).anyMatch(review -> review.author().equals("Jordan"));
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl graphql/spring-demo -am -Dtest=ReviewServiceTest`
Expected: FAIL — `ReviewService` does not exist.

- [ ] **Step 3: Write `ReviewService`**

```java
package com.testingai.graphql.domain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory reviews keyed by product id, plus the event stream {@code addReview} publishes to for the
 * {@code reviewAdded} subscription. {@link #findByProductIds(List)} is this demo's DataLoader batch endpoint: however
 * many product ids are passed in, it runs as a single call — see {@link #batchCallCount}.
 */
@Slf4j
@Service
public class ReviewService {

	private static final List<String> SEED_AUTHORS = List.of("Alex", "Priya", "Sam");

	private final Map<String, List<Review>> reviewsByProductId = new ConcurrentHashMap<>();
	private final Sinks.Many<Review> reviewAddedSink = Sinks.many().multicast().onBackpressureBuffer();
	private final AtomicInteger batchCallCount = new AtomicInteger();

	public ReviewService(ProductCatalogService productCatalogService) {
		seedReviews(productCatalogService);
	}

	private void seedReviews(ProductCatalogService productCatalogService) {
		int authorIndex = 0;
		for (Product product : productCatalogService.listProducts()) {
			int reviewCount = Integer.parseInt(product.id().substring(1)) % 3;
			List<Review> seeded = new CopyOnWriteArrayList<>();
			for (int i = 0; i < reviewCount; i++) {
				String author = SEED_AUTHORS.get(authorIndex % SEED_AUTHORS.size());
				authorIndex++;
				int rating = 3 + (i % 3);
				seeded.add(new Review(UUID.randomUUID().toString(), product.id(), author, rating,
						author + "'s review #" + (i + 1) + " of " + product.name()));
			}
			reviewsByProductId.put(product.id(), seeded);
		}
	}

	/**
	 * Batch-fetches reviews for every product id in one call — the DataLoader pattern that avoids the N+1 problem
	 * when resolving {@code Product.reviews} for a list of products in a single GraphQL query.
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

	public Review addReview(String productId, String author, int rating, String comment) {
		Review review = new Review(UUID.randomUUID().toString(), productId, author, rating, comment);
		reviewsByProductId.computeIfAbsent(productId, key -> new CopyOnWriteArrayList<>()).add(review);
		reviewAddedSink.tryEmitNext(review);
		return review;
	}

	public Flux<Review> reviewAdded() {
		return reviewAddedSink.asFlux();
	}

	public int getBatchCallCount() {
		return batchCallCount.get();
	}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl graphql/spring-demo -am -Dtest=ReviewServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/ReviewService.java communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/domain/ReviewServiceTest.java
git commit -m "feat(communication-protocols): add ReviewService with batch fetch and subscription sink"
```

---

### Task 5: GraphQL schema + Query pattern + error classification

**Files:**
- Create: `communication-protocols/graphql/spring-demo/src/main/resources/graphql/schema.graphqls`
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/exception/DemoExceptionResolver.java`
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/controller/DemoController.java`
- Test: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/exception/DemoExceptionResolverTest.java`
- Test: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/DemoControllerTest.java`
- Test: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/DemoIntegrationTest.java`

**Interfaces:**
- Consumes: `ProductCatalogService` (Task 3), `FailureSimulator.maybeThrow(String)` (Task 2).
- Produces: `DemoController.products(): List<Product>`, `DemoController.product(String id): Product` (nullable); `DemoExceptionResolver` classifying `IllegalArgumentException` → `BAD_REQUEST`, everything else → `INTERNAL_ERROR`. `DemoController` constructor takes `(ProductCatalogService, ReviewService)` — `ReviewService` is wired now even though only used starting Task 6, so the constructor shape doesn't change again later.

- [ ] **Step 1: Write the GraphQL schema**

`communication-protocols/graphql/spring-demo/src/main/resources/graphql/schema.graphqls`:

```graphql
type Product {
    id: ID!
    name: String!
    priceCents: Int!
    reviews: [Review!]!
}

type Review {
    id: ID!
    productId: ID!
    author: String!
    rating: Int!
    comment: String
}

type Query {
    products: [Product!]!
    product(id: ID!): Product
}

type Mutation {
    addReview(input: AddReviewInput!): Review!
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

- [ ] **Step 2: Write the failing exception-resolver test**

```java
package com.testingai.graphql.exception;

import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.execution.ErrorType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DemoExceptionResolverTest {

	private final DemoExceptionResolver resolver = new DemoExceptionResolver();

	@Test
	void resolveException_classifiesRuntimeExceptionAsInternalError() {
		DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);

		List<GraphQLError> errors = resolver.resolveException(new RuntimeException("Simulated 5% failure"), env)
				.block();

		assertThat(errors).hasSize(1);
		assertThat(errors.get(0).getMessage()).isEqualTo("Simulated 5% failure");
		assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
	}

	@Test
	void resolveException_classifiesIllegalArgumentExceptionAsBadRequest() {
		DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);

		List<GraphQLError> errors = resolver
				.resolveException(new IllegalArgumentException("Unknown product: p99"), env).block();

		assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
	}
}
```

Note: the test message deliberately includes a literal `%` character to prove the resolver's `.message(...)` call does not run it through `String.format` (which would otherwise throw on an unpaired `%`).

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn test -pl graphql/spring-demo -am -Dtest=DemoExceptionResolverTest`
Expected: FAIL — `DemoExceptionResolver` does not exist.

- [ ] **Step 4: Write `DemoExceptionResolver`**

```java
package com.testingai.graphql.exception;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

/**
 * Classifies exceptions thrown from data fetchers into typed GraphQL errors instead of leaking a raw stack trace:
 * {@link IllegalArgumentException} (e.g. {@code addReview} against an unknown product) becomes {@code BAD_REQUEST};
 * anything else — including {@link com.testingai.graphql.util.FailureSimulator}'s simulated failures — becomes
 * {@code INTERNAL_ERROR}.
 */
@Component
public class DemoExceptionResolver extends DataFetcherExceptionResolverAdapter {

	@Override
	protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
		ErrorType errorType = ex instanceof IllegalArgumentException ? ErrorType.BAD_REQUEST : ErrorType.INTERNAL_ERROR;
		return GraphqlErrorBuilder.newError().errorType(errorType).message(ex.getMessage()).build();
	}
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -pl graphql/spring-demo -am -Dtest=DemoExceptionResolverTest`
Expected: PASS

- [ ] **Step 6: Write the failing `DemoControllerTest` (query methods only)**

```java
package com.testingai.graphql.controller;

import com.testingai.graphql.domain.Product;
import com.testingai.graphql.domain.ProductCatalogService;
import com.testingai.graphql.domain.ReviewService;
import com.testingai.graphql.util.FailureSimulator;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

class DemoControllerTest {

	private final ProductCatalogService productCatalogService = new ProductCatalogService();
	private final ReviewService reviewService = new ReviewService(productCatalogService);
	private final DemoController controller = new DemoController(productCatalogService, reviewService);

	@Test
	void products_returnsFullCatalog() {
		assertThat(controller.products()).hasSize(40);
	}

	@Test
	void product_returnsProduct_whenFound() {
		try (MockedStatic<FailureSimulator> mocked = mockStatic(FailureSimulator.class)) {
			mocked.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(invocation -> null);

			Product product = controller.product("p1");

			assertThat(product.name()).isEqualTo("Mini Widget");
		}
	}

	@Test
	void product_returnsNull_whenUnknown() {
		try (MockedStatic<FailureSimulator> mocked = mockStatic(FailureSimulator.class)) {
			mocked.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(invocation -> null);

			assertThat(controller.product("unknown")).isNull();
		}
	}

	@Test
	void product_propagatesException_onSimulatedFailure() {
		try (MockedStatic<FailureSimulator> mocked = mockStatic(FailureSimulator.class)) {
			mocked.when(() -> FailureSimulator.maybeThrow(anyString())).thenThrow(new RuntimeException("Simulated"));

			assertThatThrownBy(() -> controller.product("p1")).isInstanceOf(RuntimeException.class)
					.hasMessage("Simulated");
		}
	}
}
```

- [ ] **Step 7: Run test to verify it fails**

Run: `mvn test -pl graphql/spring-demo -am -Dtest=DemoControllerTest`
Expected: FAIL — `DemoController` does not exist.

- [ ] **Step 8: Write `DemoController` (query methods only for now)**

```java
package com.testingai.graphql.controller;

import com.testingai.graphql.domain.Product;
import com.testingai.graphql.domain.ProductCatalogService;
import com.testingai.graphql.domain.ReviewService;
import com.testingai.graphql.util.FailureSimulator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * Hosts every GraphQL operation for this demo — queries, the batch-mapped {@code reviews} field, the mutation, and
 * the subscription all live here, mirroring how {@code grpc/client-demo}'s {@code DemoController} centralizes every
 * RPC pattern in one class.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class DemoController {

	private final ProductCatalogService productCatalogService;
	private final ReviewService reviewService;

	/**
	 * Query — returns the full in-memory catalog.
	 */
	@QueryMapping
	public List<Product> products() {
		log.info("[products] returning {} products", productCatalogService.listProducts().size());
		return productCatalogService.listProducts();
	}

	/**
	 * Query — looks up one product by id. Has a 5% simulated failure via {@link FailureSimulator}, demonstrating
	 * GraphQL's partial-failure behavior: this field's error is reported in the response's {@code errors[]} array
	 * without failing sibling fields in the same request.
	 */
	@QueryMapping
	public Product product(@Argument String id) {
		log.info("[product] looking up productId={}", id);
		FailureSimulator.maybeThrow("product query");
		return productCatalogService.findProduct(id).orElse(null);
	}
}
```

- [ ] **Step 9: Run `DemoControllerTest` to verify it passes**

Run: `mvn test -pl graphql/spring-demo -am -Dtest=DemoControllerTest`
Expected: PASS

- [ ] **Step 10: Write the failing `DemoIntegrationTest` (query + error case only)**

```java
package com.testingai.graphql.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoIntegrationTest {

	@LocalServerPort
	private int port;

	private HttpGraphQlTester graphQlTester;

	@BeforeEach
	void setUpTester() {
		WebTestClient webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port + "/graphql")
				.build();
		graphQlTester = HttpGraphQlTester.create(webTestClient);
	}

	@Test
	void query_returnsAllProducts() {
		graphQlTester.document("""
				query {
				  products { id name }
				}
				""").execute().path("products").entityList(Object.class).hasSize(40);
	}

	@Test
	void query_returnsOneProduct_byId() {
		graphQlTester.document("""
				query {
				  product(id: "p1") { id name }
				}
				""").execute().path("product.name").entity(String.class).isEqualTo("Mini Widget");
	}

	@Test
	void query_partiallyFails_whenProductLookupSimulatesFailure() {
		// FailureSimulator's 5% failure is real here, not mocked: the GraphQL execution runs on a Tomcat
		// worker thread, not the test thread, so Mockito's thread-confined mockStatic can't reach it. Instead,
		// repeat until one of the ~5%-chance failures actually happens (same statistical approach as
		// FailureSimulatorTest) and assert on that response's partial-failure shape.
		String query = """
				query {
				  products { id }
				  product(id: "p1") { id name }
				}
				""";

		for (int attempt = 0; attempt < 200; attempt++) {
			List<ResponseError> errors = new ArrayList<>();
			GraphQlTester.Traversable afterErrors = graphQlTester.document(query).execute().errors()
					.satisfy(errors::addAll);

			if (!errors.isEmpty()) {
				assertThat(errors).hasSize(1);
				assertThat(errors.get(0).getMessage()).isEqualTo("Simulated 5% failure in product query");
				assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
				afterErrors.path("product").valueIsNull();
				afterErrors.path("products").entityList(Object.class).hasSize(40);
				return;
			}
		}

		fail("Expected at least one simulated failure across 200 attempts (5% failure rate)");
	}
}
```

- [ ] **Step 11: Run test to verify it fails, then passes**

Run: `mvn test -pl graphql/spring-demo -am -Dtest=DemoIntegrationTest`
Expected: first run FAILs to compile if any typo exists — fix until all 3 tests PASS. This is the test that proves the schema, `DemoController`, and `DemoExceptionResolver` are wired together correctly end-to-end, including the partial-failure response shape (the `product` field is `null` and carries the error, while the sibling `products` field in the same request still returns all 40 items).

- [ ] **Step 12: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/main
git add communication-protocols/graphql/spring-demo/src/test
git commit -m "feat(communication-protocols): add GraphQL query pattern and error classification"
```

---

### Task 6: DataLoader batching (`reviews` field)

**Files:**
- Modify: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/controller/DemoController.java`
- Modify: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/DemoControllerTest.java`
- Modify: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/DemoIntegrationTest.java`

**Interfaces:**
- Consumes: `ReviewService.findByProductIds(List<String>): Map<String, List<Review>>`, `ReviewService.getBatchCallCount(): int` (Task 4).
- Produces: `DemoController.reviews(List<Product>): Map<Product, List<Review>>` (Spring GraphQL `@BatchMapping` — one call per query execution, not one per product).

- [ ] **Step 1: Add the failing test to `DemoControllerTest`**

Add this test method to the existing class:

```java
	@Test
	void reviews_batchesAllProducts_inOneCall() {
		List<Product> products = productCatalogService.listProducts().subList(0, 3);

		Map<Product, List<Review>> reviewsByProduct = controller.reviews(products);

		assertThat(reviewsByProduct).hasSize(3);
		assertThat(reviewService.getBatchCallCount()).isEqualTo(1);
	}
```

Add these imports:

```java
import com.testingai.graphql.domain.Review;

import java.util.List;
import java.util.Map;
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl graphql/spring-demo -am -Dtest=DemoControllerTest`
Expected: FAIL — `DemoController.reviews(List)` does not exist.

- [ ] **Step 3: Add the `@BatchMapping` method to `DemoController`**

Replace the whole file's import block and add the method. Full updated file:

```java
package com.testingai.graphql.controller;

import com.testingai.graphql.domain.Product;
import com.testingai.graphql.domain.ProductCatalogService;
import com.testingai.graphql.domain.Review;
import com.testingai.graphql.domain.ReviewService;
import com.testingai.graphql.util.FailureSimulator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Hosts every GraphQL operation for this demo — queries, the batch-mapped {@code reviews} field, the mutation, and
 * the subscription all live here, mirroring how {@code grpc/client-demo}'s {@code DemoController} centralizes every
 * RPC pattern in one class.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class DemoController {

	private final ProductCatalogService productCatalogService;
	private final ReviewService reviewService;

	/**
	 * Query — returns the full in-memory catalog.
	 */
	@QueryMapping
	public List<Product> products() {
		log.info("[products] returning {} products", productCatalogService.listProducts().size());
		return productCatalogService.listProducts();
	}

	/**
	 * Query — looks up one product by id. Has a 5% simulated failure via {@link FailureSimulator}, demonstrating
	 * GraphQL's partial-failure behavior: this field's error is reported in the response's {@code errors[]} array
	 * without failing sibling fields in the same request.
	 */
	@QueryMapping
	public Product product(@Argument String id) {
		log.info("[product] looking up productId={}", id);
		FailureSimulator.maybeThrow("product query");
		return productCatalogService.findProduct(id).orElse(null);
	}

	/**
	 * Batch mapping for {@code Product.reviews} — the DataLoader pattern. However many products are being resolved
	 * in a single query, this method runs exactly once, fetching every product's reviews in one call to
	 * {@link ReviewService#findByProductIds(List)} instead of once per product (the N+1 problem).
	 */
	@BatchMapping
	public Map<Product, List<Review>> reviews(List<Product> products) {
		List<String> productIds = products.stream().map(Product::id).toList();
		Map<String, List<Review>> reviewsByProductId = reviewService.findByProductIds(productIds);
		return products.stream().collect(
				Collectors.toMap(product -> product, product -> reviewsByProductId.getOrDefault(product.id(), List.of())));
	}
}
```

- [ ] **Step 4: Run `DemoControllerTest` to verify it passes**

Run: `mvn test -pl graphql/spring-demo -am -Dtest=DemoControllerTest`
Expected: PASS

- [ ] **Step 5: Add the failing integration test**

Add this test method to `DemoIntegrationTest`:

```java
	@Autowired
	private ReviewService reviewService;

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

Add imports:

```java
import com.testingai.graphql.domain.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
```

- [ ] **Step 6: Run test to verify it fails, then passes**

Run: `mvn test -pl graphql/spring-demo -am -Dtest=DemoIntegrationTest`
Expected: PASS once the `@Autowired ReviewService reviewService` field compiles — this is the test that proves the DataLoader wiring works through the real GraphQL execution engine, not just the plain Java method call in `DemoControllerTest`.

- [ ] **Step 7: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src
git commit -m "feat(communication-protocols): add DataLoader batching for Product.reviews"
```

---

### Task 7: Mutation (`addReview`)

**Files:**
- Modify: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/controller/DemoController.java`
- Modify: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/DemoControllerTest.java`
- Modify: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/DemoIntegrationTest.java`

**Interfaces:**
- Consumes: `ReviewService.addReview(String, String, int, String): Review` (Task 4), `AddReviewInput` record (Task 3).
- Produces: `DemoController.addReview(AddReviewInput): Review` — throws `IllegalArgumentException` if `productId` is unknown (classified `BAD_REQUEST` by `DemoExceptionResolver` from Task 5).

- [ ] **Step 1: Add the failing tests to `DemoControllerTest`**

```java
	@Test
	void addReview_addsReview_whenProductExists() {
		Review review = controller.addReview(new AddReviewInput("p1", "Jordan", 5, "Great product"));

		assertThat(review.author()).isEqualTo("Jordan");
		assertThat(reviewService.findByProductIds(List.of("p1")).get("p1")).contains(review);
	}

	@Test
	void addReview_throws_whenProductUnknown() {
		assertThatThrownBy(() -> controller.addReview(new AddReviewInput("unknown", "Jordan", 5, "comment")))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unknown");
	}
```

Add import: `import com.testingai.graphql.domain.AddReviewInput;`

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl graphql/spring-demo -am -Dtest=DemoControllerTest`
Expected: FAIL — `DemoController.addReview(AddReviewInput)` does not exist.

- [ ] **Step 3: Add the mutation method to `DemoController`**

Add this method to the class (and the two new imports below):

```java
	/**
	 * Mutation — adds a review to a product and publishes it to {@link #reviewAdded} subscribers.
	 */
	@MutationMapping
	public Review addReview(@Argument AddReviewInput input) {
		log.info("[addReview] productId={} author={} rating={}", input.productId(), input.author(), input.rating());
		if (productCatalogService.findProduct(input.productId()).isEmpty()) {
			throw new IllegalArgumentException("Unknown product: " + input.productId());
		}
		return reviewService.addReview(input.productId(), input.author(), input.rating(), input.comment());
	}
```

Add imports to `DemoController.java`:

```java
import com.testingai.graphql.domain.AddReviewInput;
import org.springframework.graphql.data.method.annotation.MutationMapping;
```

- [ ] **Step 4: Run `DemoControllerTest` to verify it passes**

Run: `mvn test -pl graphql/spring-demo -am -Dtest=DemoControllerTest`
Expected: PASS

- [ ] **Step 5: Add the failing integration test**

Add to `DemoIntegrationTest`:

```java
	@Test
	void mutation_addReview_returnsCreatedReview() {
		graphQlTester.document("""
				mutation {
				  addReview(input: { productId: "p1", author: "Jordan", rating: 5, comment: "Great product" }) {
				    author
				    rating
				    comment
				  }
				}
				""").execute().path("addReview.author").entity(String.class).isEqualTo("Jordan");
	}

	@Test
	void mutation_addReview_isRejected_whenProductUnknown() {
		graphQlTester.document("""
				mutation {
				  addReview(input: { productId: "unknown", author: "Jordan", rating: 5, comment: "x" }) {
				    id
				  }
				}
				""").execute().errors().satisfy(errors -> {
			// addReview is a non-nullable field (Review!), so throwing here also produces graphql-java's own
			// "null value for non-nullable field" error alongside our classified one — assert ours is present
			// rather than assuming it's the only error.
			assertThat(errors).anySatisfy(error -> assertThat(error.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
		});
	}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn test -pl graphql/spring-demo -am -Dtest=DemoIntegrationTest`
Expected: PASS (all 6 tests in this class so far)

- [ ] **Step 7: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src
git commit -m "feat(communication-protocols): add addReview mutation"
```

---

### Task 8: Subscription (`reviewAdded`)

**Files:**
- Modify: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/controller/DemoController.java`
- Modify: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/DemoControllerTest.java`
- Modify: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/DemoIntegrationTest.java`

**Interfaces:**
- Consumes: `ReviewService.reviewAdded(): Flux<Review>` (Task 4).
- Produces: `DemoController.reviewAdded(String productId): Flux<Review>` — filters to one product when `productId` is non-null, otherwise streams every review.

- [ ] **Step 1: Add the failing test to `DemoControllerTest`**

```java
	@Test
	void reviewAdded_filtersByProductId() {
		StepVerifier.create(controller.reviewAdded("p1").take(1))
				.then(() -> controller.addReview(new AddReviewInput("p2", "Jordan", 5, "not p1")))
				.then(() -> controller.addReview(new AddReviewInput("p1", "Sam", 4, "for p1")))
				.assertNext(review -> assertThat(review.productId()).isEqualTo("p1"))
				.verifyComplete();
	}
```

Add import: `import reactor.test.StepVerifier;`

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl graphql/spring-demo -am -Dtest=DemoControllerTest`
Expected: FAIL — `DemoController.reviewAdded(String)` does not exist.

- [ ] **Step 3: Add the subscription method to `DemoController`**

```java
	/**
	 * Subscription — streams every review added from this point on, optionally filtered to one product.
	 */
	@SubscriptionMapping
	public Flux<Review> reviewAdded(@Argument String productId) {
		log.info("[reviewAdded] subscription opened, productId={}", productId);
		Flux<Review> stream = reviewService.reviewAdded();
		return productId == null ? stream : stream.filter(review -> review.productId().equals(productId));
	}
```

Add imports to `DemoController.java`:

```java
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import reactor.core.publisher.Flux;
```

- [ ] **Step 4: Run `DemoControllerTest` to verify it passes**

Run: `mvn test -pl graphql/spring-demo -am -Dtest=DemoControllerTest`
Expected: PASS (all 8 tests in this class)

- [ ] **Step 5: Add the failing WebSocket subscription integration test**

Add this test and its supporting fields/lifecycle methods to `DemoIntegrationTest`:

```java
	private WebSocketGraphQlTester webSocketGraphQlTester;

	@BeforeEach
	void setUpWebSocketTester() {
		webSocketGraphQlTester = WebSocketGraphQlTester
				.builder("ws://localhost:" + port + "/graphql", new TomcatWebSocketClient()).build();
	}

	@AfterEach
	void stopWebSocketTester() {
		webSocketGraphQlTester.stop().block();
	}

	@Test
	void subscription_streamsReviewAdded_whenMutationPublishes() {
		Flux<Review> subscription = webSocketGraphQlTester.document("""
				subscription {
				  reviewAdded(productId: "p1") {
				    id
				    productId
				    author
				    rating
				    comment
				  }
				}
				""").executeSubscription().toFlux("reviewAdded", Review.class);

		StepVerifier.create(subscription)
				.then(() -> graphQlTester.document("""
						mutation {
						  addReview(input: { productId: "p1", author: "Riley", rating: 4, comment: "Solid" }) {
						    id
						  }
						}
						""").execute())
				.assertNext(review -> assertThat(review.author()).isEqualTo("Riley"))
				.thenCancel()
				.verify(Duration.ofSeconds(5));
	}
```

Add imports:

```java
import com.testingai.graphql.domain.Review;
import org.junit.jupiter.api.AfterEach;
import org.springframework.graphql.test.tester.WebSocketGraphQlTester;
import org.springframework.web.reactive.socket.client.TomcatWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
```

Note: `TomcatWebSocketClient` (from `spring-webflux`, test-scope dependency added in Task 1) needs the JSR-356 (`jakarta.websocket`) client classes, which are already on the classpath via `spring-boot-starter-web` → `spring-boot-starter-tomcat` → `tomcat-embed-websocket`. No new production dependency is needed for this test.

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn test -pl graphql/spring-demo -am -Dtest=DemoIntegrationTest`
Expected: PASS (all 7 tests in this class). If it times out, double check `application.yml` has `spring.graphql.websocket.path: /graphql` (Task 1) — without it, the WebSocket endpoint isn't registered and the client can't connect.

- [ ] **Step 7: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src
git commit -m "feat(communication-protocols): add reviewAdded subscription"
```

---

### Task 9: Gatling load test

**Files:**
- Create: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/performance/DemoSimulation.java`

**Interfaces:**
- Consumes: the running app's `/graphql` HTTP endpoint (manual verification only — this class is excluded from `mvn test`).

- [ ] **Step 1: Write the simulation**

```java
package com.testingai.graphql.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DemoSimulation extends Simulation {

	private static final String PRODUCTS_QUERY_BODY = """
			{"query":"{ products { id name priceCents reviews { id author rating } } }"}""";

	private static final String PRODUCT_QUERY_BODY = """
			{"query":"{ product(id: \\"p1\\") { id name priceCents } }"}""";

	private static final String ADD_REVIEW_MUTATION_BODY = """
			{"query":"mutation { addReview(input: { productId: \\"p1\\", author: \\"LoadTest\\", rating: 5, comment: \\"Load test review\\" }) { id } }"}""";

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8092")
			.contentTypeHeader("application/json");

	private final ScenarioBuilder demoScenario = scenario("GraphQL Demo")
			.exec(http("Query - Products with Reviews").post("/graphql").body(StringBody(PRODUCTS_QUERY_BODY))
					.check(status().is(200)))
			.pause(Duration.ofMillis(500))
			.exec(http("Query - Product by Id").post("/graphql").body(StringBody(PRODUCT_QUERY_BODY))
					.check(status().is(200)))
			.pause(Duration.ofMillis(500))
			.exec(http("Mutation - Add Review").post("/graphql").body(StringBody(ADD_REVIEW_MUTATION_BODY))
					.check(status().is(200)));

	{
		// 2 users, ramped a few seconds apart, so each user's calls stay visually distinct in the logs instead of
		// interleaving with a burst of traffic — matches grpc/client-demo's DemoSimulation pacing.
		setUp(demoScenario.injectOpen(rampUsers(2).during(Duration.ofSeconds(6)))).protocols(httpProtocol)
				.maxDuration(Duration.ofSeconds(90));
	}
}
```

- [ ] **Step 2: Verify it's excluded from `mvn test`**

Run: `mvn test -pl graphql/spring-demo -am`
Expected: `DemoSimulation` is NOT in the list of run tests (matches the inherited `**/performance/**` surefire exclude from the parent POM).

- [ ] **Step 3: Manually verify the Gatling run (requires the app running)**

In one terminal: `mvn spring-boot:run -pl graphql/spring-demo`
In another terminal: `mvn gatling:test -pl graphql/spring-demo`
Expected: `BUILD SUCCESS`, HTML report generated under `graphql/spring-demo/target/gatling/`, all requests green. Note that `PRODUCT_QUERY_BODY` queries `product(id: "p1")`, which has a 5% simulated failure — but GraphQL always responds `200` even when a field fails (the failure shows up in the response's `errors[]` array instead), so the `status().is(200)` check passes regardless.

- [ ] **Step 4: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/performance
git commit -m "test(communication-protocols): add Gatling load test for graphql-spring-demo"
```

---

### Task 10: JMeter load test

**Files:**
- Create: `communication-protocols/graphql/spring-demo/src/test/jmeter/DemoSimulation.jmx`

**Interfaces:**
- Consumes: the running app's `/graphql` HTTP endpoint (manual verification only — gated behind the `jmeter-load-test` Maven profile, never bound to the default build).

- [ ] **Step 1: Write the JMeter test plan**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="GraphQL Demo">
      <stringProp name="TestPlan.comments">Load test for the graphql-spring-demo app. Mirrors com.testingai.graphql.performance.DemoSimulation (Gatling) with the same request bodies and pacing story, run via JMeter instead. Requires graphql-spring-demo to be running.</stringProp>
      <boolProp name="TestPlan.tearDown_on_shutdown">true</boolProp>
      <elementProp name="TestPlan.user_defined_variables" elementType="Arguments" guiclass="ArgumentsPanel" testclass="Arguments" testname="User Defined Variables">
        <collectionProp name="Arguments.arguments"/>
      </elementProp>
    </TestPlan>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="GraphQL Demo Users">
        <intProp name="ThreadGroup.num_threads">2</intProp>
        <intProp name="ThreadGroup.ramp_time">6</intProp>
        <boolProp name="ThreadGroup.same_user_on_next_iteration">true</boolProp>
        <stringProp name="ThreadGroup.on_sample_error">continue</stringProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController" guiclass="LoopControlPanel" testclass="LoopController" testname="Loop Controller">
          <stringProp name="LoopController.loops">1</stringProp>
          <boolProp name="LoopController.continue_forever">false</boolProp>
        </elementProp>
      </ThreadGroup>
      <hashTree>
        <HeaderManager guiclass="HeaderPanel" testclass="HeaderManager" testname="HTTP Header Manager" enabled="true">
          <collectionProp name="HeaderManager.headers">
            <elementProp name="" elementType="Header">
              <stringProp name="Header.name">Content-Type</stringProp>
              <stringProp name="Header.value">application/json</stringProp>
            </elementProp>
          </collectionProp>
        </HeaderManager>
        <hashTree/>
        <ConstantTimer guiclass="ConstantTimerGui" testclass="ConstantTimer" testname="Pause Between Calls" enabled="true">
          <stringProp name="ConstantTimer.delay">500</stringProp>
        </ConstantTimer>
        <hashTree/>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="Query - Products with Reviews" enabled="true">
          <stringProp name="HTTPSampler.domain">localhost</stringProp>
          <stringProp name="HTTPSampler.port">8092</stringProp>
          <stringProp name="HTTPSampler.protocol">http</stringProp>
          <stringProp name="HTTPSampler.path">/graphql</stringProp>
          <boolProp name="HTTPSampler.follow_redirects">true</boolProp>
          <stringProp name="HTTPSampler.method">POST</stringProp>
          <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
          <boolProp name="HTTPSampler.postBodyRaw">true</boolProp>
          <elementProp name="HTTPsampler.Arguments" elementType="Arguments">
            <collectionProp name="Arguments.arguments">
              <elementProp name="" elementType="HTTPArgument">
                <boolProp name="HTTPArgument.always_encode">false</boolProp>
                <stringProp name="Argument.value">{&quot;query&quot;:&quot;{ products { id name priceCents reviews { id author rating } } }&quot;}</stringProp>
                <stringProp name="Argument.metadata">=</stringProp>
              </elementProp>
            </collectionProp>
          </elementProp>
        </HTTPSamplerProxy>
        <hashTree/>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="Query - Product by Id" enabled="true">
          <stringProp name="HTTPSampler.domain">localhost</stringProp>
          <stringProp name="HTTPSampler.port">8092</stringProp>
          <stringProp name="HTTPSampler.protocol">http</stringProp>
          <stringProp name="HTTPSampler.path">/graphql</stringProp>
          <boolProp name="HTTPSampler.follow_redirects">true</boolProp>
          <stringProp name="HTTPSampler.method">POST</stringProp>
          <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
          <boolProp name="HTTPSampler.postBodyRaw">true</boolProp>
          <elementProp name="HTTPsampler.Arguments" elementType="Arguments">
            <collectionProp name="Arguments.arguments">
              <elementProp name="" elementType="HTTPArgument">
                <boolProp name="HTTPArgument.always_encode">false</boolProp>
                <stringProp name="Argument.value">{&quot;query&quot;:&quot;{ product(id: \&quot;p1\&quot;) { id name priceCents } }&quot;}</stringProp>
                <stringProp name="Argument.metadata">=</stringProp>
              </elementProp>
            </collectionProp>
          </elementProp>
        </HTTPSamplerProxy>
        <hashTree/>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="Mutation - Add Review" enabled="true">
          <stringProp name="HTTPSampler.domain">localhost</stringProp>
          <stringProp name="HTTPSampler.port">8092</stringProp>
          <stringProp name="HTTPSampler.protocol">http</stringProp>
          <stringProp name="HTTPSampler.path">/graphql</stringProp>
          <boolProp name="HTTPSampler.follow_redirects">true</boolProp>
          <stringProp name="HTTPSampler.method">POST</stringProp>
          <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
          <boolProp name="HTTPSampler.postBodyRaw">true</boolProp>
          <elementProp name="HTTPsampler.Arguments" elementType="Arguments">
            <collectionProp name="Arguments.arguments">
              <elementProp name="" elementType="HTTPArgument">
                <boolProp name="HTTPArgument.always_encode">false</boolProp>
                <stringProp name="Argument.value">{&quot;query&quot;:&quot;mutation { addReview(input: { productId: \&quot;p1\&quot;, author: \&quot;LoadTest\&quot;, rating: 5, comment: \&quot;Load test review\&quot; }) { id } }&quot;}</stringProp>
                <stringProp name="Argument.metadata">=</stringProp>
              </elementProp>
            </collectionProp>
          </elementProp>
        </HTTPSamplerProxy>
        <hashTree/>
        <ResultCollector guiclass="SummaryReport" testclass="ResultCollector" testname="Summary Report" enabled="true">
          <boolProp name="ResultCollector.error_logging">false</boolProp>
          <objProp>
            <name>saveConfig</name>
            <value class="SampleSaveConfiguration">
              <time>true</time>
              <latency>true</latency>
              <timestamp>true</timestamp>
              <success>true</success>
              <label>true</label>
              <code>true</code>
              <message>true</message>
              <threadName>true</threadName>
              <dataType>true</dataType>
              <encoding>false</encoding>
              <assertions>true</assertions>
              <subresults>true</subresults>
              <responseData>false</responseData>
              <samplerData>false</samplerData>
              <xml>false</xml>
              <fieldNames>true</fieldNames>
              <responseHeaders>false</responseHeaders>
              <requestHeaders>false</requestHeaders>
              <responseDataOnError>false</responseDataOnError>
              <saveAssertionResultsFailureMessage>true</saveAssertionResultsFailureMessage>
              <assertionsResultsToSave>0</assertionsResultsToSave>
              <bytes>true</bytes>
              <sentBytes>true</sentBytes>
              <url>true</url>
              <threadCounts>true</threadCounts>
              <idleTime>true</idleTime>
              <connectTime>true</connectTime>
            </value>
          </objProp>
          <stringProp name="filename"></stringProp>
        </ResultCollector>
        <hashTree/>
      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
```

- [ ] **Step 2: Manually verify the JMeter run (requires the app running)**

In one terminal: `mvn spring-boot:run -pl graphql/spring-demo`
In another terminal: `mvn verify -Pjmeter-load-test -pl graphql/spring-demo`
Expected: `BUILD SUCCESS`, a summary printed to the console, raw results under `graphql/spring-demo/target/jmeter/results/`.

- [ ] **Step 3: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/test/jmeter
git commit -m "test(communication-protocols): add JMeter load test for graphql-spring-demo"
```

---

### Task 11: Documentation, formatting, and final verification

**Files:**
- Modify: `communication-protocols/README.md`
- Create: `communication-protocols/graphql/README.md`
- Create: `communication-protocols/graphql/spring-demo/README.md`
- Modify: `CLAUDE.md`

**Interfaces:** None — this task only touches documentation and runs verification commands; no production code changes.

- [ ] **Step 1: Update `communication-protocols/README.md`**

Replace:

```markdown
| Protocol | Demo | Best fit |
|---|---|---|
| [gRPC](grpc/) | Two independent Spring Boot apps (server + client) covering all four RPC patterns | High-performance, strongly-typed service-to-service calls; streaming workloads |

More protocol demos may be added here over time (e.g. GraphQL, WebSocket).
```

with:

```markdown
| Protocol | Demo | Best fit |
|---|---|---|
| [gRPC](grpc/) | Two independent Spring Boot apps (server + client) covering all four RPC patterns | High-performance, strongly-typed service-to-service calls; streaming workloads |
| [GraphQL](graphql/) | Single Spring Boot app covering query/nested-fetch, DataLoader batching, mutation, and subscription patterns | Client-driven field selection over one endpoint; aggregating/relational data from a single request |

More protocol demos may be added here over time (e.g. WebSocket).
```

- [ ] **Step 2: Write `communication-protocols/graphql/README.md`**

```markdown
# GraphQL Demo

Demonstrates GraphQL — a schema-first query language for APIs, served over a single endpoint (`/graphql`), where the client specifies exactly which fields it wants — via one Spring Boot app (`spring-demo`) built on `spring-boot-starter-graphql`, against a Products↔Reviews domain.

Unlike the [gRPC demo](../grpc/), GraphQL doesn't need a client/server split: any HTTP (or WebSocket, for subscriptions) client can talk to the schema directly, so this module is a single app.

## The four patterns

| Pattern | Field/operation | What it demonstrates |
|---|---|---|
| Query + nested fetch | `products { reviews { ... } }` | Client asks for exactly the fields it wants, including a nested child collection, in one round trip |
| DataLoader batching | `Product.reviews` via `@BatchMapping` | Solves the N+1 problem: fetching `reviews` for N products in one query triggers **one** batched call, not N |
| Mutation | `addReview` | A write that returns the created object and publishes it to the subscription stream |
| Subscription | `reviewAdded(productId)` | Real-time push over a GraphQL-over-WebSocket session, optionally filtered server-side by `productId` |

### Query + nested fetch

**Pros**
- Client controls the response shape exactly — no over-fetching or under-fetching
- One round trip covers what would otherwise be several REST calls (e.g. product + its reviews)
- Self-documenting: the schema is the contract, browsable via GraphiQL

**Cons**
- Naive nested-field resolution is prone to the N+1 problem (see below)
- Caching a whole response as one unit is harder than with REST's per-URL caching
- Arbitrary client-specified queries can be expensive to compute without query cost limits (out of scope here)

**Typical use cases**
- Aggregating data from multiple related entities in one request
- Clients (mobile, various frontends) that each need a different subset/shape of the same data
- Replacing several REST endpoints with one flexible one

### DataLoader batching

**Pros**
- Solves GraphQL's signature performance pitfall: naively resolving a nested field per-parent (`Product.reviews` for each of N products) is one call each = N+1 total calls
- One batched call for the whole set of parents in a query, regardless of how many there are
- Transparent to the schema/client — no change to the query shape, just to how the server resolves it

**Cons**
- Requires deliberate implementation (`@BatchMapping` in Spring GraphQL, or a `DataLoader` registry in vanilla graphql-java) — nothing prevents writing the naive N+1 version by mistake
- Batching only helps within a single query execution; it doesn't cache across separate requests

**Typical use cases**
- Any one-to-many nested field resolved from a different data source than its parent (reviews per product, comments per post, orders per customer)
- Anywhere a REST API would separately need "include" or "expand" query parameters

### Mutation

**Pros**
- Same request/response ergonomics as a query — client still specifies which fields of the result it wants back
- Explicit separation from queries makes read/write intent unambiguous in the schema

**Cons**
- No built-in idempotency or optimistic-concurrency story — same as a REST POST, this is left to the application
- A single mutation is one write; batching multiple writes into one round trip needs a custom input shape (e.g. a list input), not built into the spec

**Typical use cases**
- Any create/update/delete operation
- Actions that should return the resulting object shaped by client-specified fields (e.g. return the id and computed fields right after creating something)

### Subscription

**Pros**
- Real-time push without polling, over a single persistent connection (WebSocket)
- Same field-selection ergonomics as queries — the client only receives the fields it asked for
- Can be filtered server-side per subscription (e.g. one client only wants updates for one product)

**Cons**
- Needs a stateful, persistent connection — different operational story than plain request/response (reconnect/backoff, connection limits)
- This demo's event stream is in-memory (`Sinks.Many`), so it's single-instance only; a multi-instance deployment needs an external pub/sub backing it
- Harder to test and debug than request/response patterns

**Typical use cases**
- Live updates: new reviews/comments, order status changes, notifications
- Dashboards and UIs that should reflect server-side changes without polling

## Running the demo

No Docker required — everything is in-memory.

```bash
cd communication-protocols
mvn -pl graphql/spring-demo spring-boot:run
```

GraphiQL (interactive schema explorer): http://localhost:8092/graphiql

See [spring-demo/README.md](spring-demo/README.md) for `curl` and subscription walkthroughs of all four patterns.

## Scope

In-memory data only, no persistence, no authentication/authorization, no query depth/complexity limiting, no persisted queries, no GraphQL federation — this is a protocol-pattern demo, not a production-hardening guide (same spirit as the gRPC demo's "no TLS" scope limit). Subscriptions are backed by a single in-process `Sinks.Many`, so this is a single-instance demo only.
```

- [ ] **Step 3: Write `communication-protocols/graphql/spring-demo/README.md`**

```markdown
# GraphQL Spring Demo

Single Spring Boot app exposing a GraphQL schema over `Product`/`Review` data, covering query + nested fetch, DataLoader batching, mutation, and subscription.

## Prerequisites

Java 21, Maven. No Docker.

## Run

```bash
cd communication-protocols
mvn -pl graphql/spring-demo spring-boot:run
```

GraphiQL: http://localhost:8092/graphiql

## Walkthrough

**Query — full catalog:**

```bash
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ products { id name priceCents } }"}'
```

**Query — one product with nested reviews (the DataLoader pattern):**

```bash
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ products { id name reviews { author rating comment } } }"}'
```

Watch the console: even though this fetches reviews for 40 products, `ReviewService` logs `batch fetching reviews for 40 products in one call` exactly once — the whole point of `@BatchMapping`. A naive per-product resolver would instead log (and query) once per product, 40 times.

**Mutation — add a review:**

```bash
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"mutation { addReview(input: { productId: \"p1\", author: \"Jordan\", rating: 5, comment: \"Great product\" }) { id author rating } }"}'
```

**Subscription — watch reviews arrive in real time:**

Open GraphiQL at http://localhost:8092/graphiql, run:

```graphql
subscription {
  reviewAdded(productId: "p1") {
    id
    author
    rating
    comment
  }
}
```

Then, in another tab or via `curl`, run the mutation above (with `productId: "p1"`) — the subscription tab receives the new review immediately. Omit `productId` in the subscription to receive reviews for every product.

**Simulated failure:** the `product(id: ...)` query has a 5% chance of failing (`FailureSimulator`). When it does, the response is still HTTP `200` (GraphQL's convention), but the body's `errors` array carries the failure, `data.product` is `null`, and any other field requested in the same query is unaffected:

```bash
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ products { id } product(id: \"p1\") { id name } }"}'
# repeat a few times to see it trip:
# {"errors":[{"message":"Simulated 5% failure in product query", ...}],"data":{"products":[...40 items...],"product":null}}
```

## Build & test

```bash
mvn clean package                    # build
mvn test                             # unit tests (Gatling excluded automatically)
mvn test -Dtest=ClassName            # single test class
mvn gatling:test                     # Gatling load test — requires the app to be running first
mvn verify -Pjmeter-load-test        # JMeter load test — requires the app to be running first
```

- **Gatling**: `com.testingai.graphql.performance.DemoSimulation` (`src/test/java/.../performance/`). Excluded from `mvn test` automatically; run with `mvn gatling:test`. HTML report under `target/gatling/`.
- **JMeter**: `src/test/jmeter/DemoSimulation.jmx` — open it in the JMeter GUI to inspect or edit it visually, either with a local JMeter install (`jmeter -t src/test/jmeter/DemoSimulation.jmx`) or via the plugin (`mvn jmeter:configure jmeter:gui`). Only wired up behind the `jmeter-load-test` Maven profile, so `mvn clean package`/`mvn verify` without `-Pjmeter-load-test` never touches JMeter. Raw per-sample results (CSV) land in `target/jmeter/results/`; a summary is also printed to the console as the run progresses.

Both load tests drive the same three requests (products+reviews query, product-by-id query, addReview mutation) with the same pacing story — 2 users ramped a few seconds apart, 500ms between calls — designed to be watched in the app's logs rather than to measure throughput. Subscriptions aren't covered by either load test since they're WebSocket sessions, not request/response calls.
```

- [ ] **Step 4: Update `CLAUDE.md`**

Add a new command section, placed after the "gRPC communication protocol demo" section and before "Project Reactor demo":

```markdown
### GraphQL communication protocol demo (run from the reactor root, no docker infrastructure required)

```bash
cd communication-protocols

mvn clean package                                  # build (part of the reactor build)
mvn test -pl graphql/spring-demo                    # unit tests (Gatling excluded automatically)
mvn test -pl graphql/spring-demo -Dtest=ClassName    # single test class
mvn -pl graphql/spring-demo spring-boot:run          # run the app (GraphiQL at :8092/graphiql)
mvn gatling:test -pl graphql/spring-demo             # Gatling load test — requires the app running first
mvn verify -Pjmeter-load-test -pl graphql/spring-demo  # JMeter load test — requires the app running first
```
```

Also update the repository layout table, adding a row after the `communication-protocols/grpc/...` row:

```markdown
| `communication-protocols/graphql/spring-demo/` | GraphQL demo — single Spring Boot app covering query/nested-fetch, DataLoader batching, mutation, and subscription patterns against a Products↔Reviews domain — no external infrastructure required |
```

- [ ] **Step 5: Run Spotless formatting**

Run: `cd communication-protocols && mvn spotless:apply && cd ..`
Expected: `BUILD SUCCESS`; check `git diff` afterward — if any file changed, that's expected (auto-formatting), review and keep the changes.

- [ ] **Step 6: Full build and test verification**

Run:

```bash
cd communication-protocols
mvn clean package
mvn test -pl graphql/spring-demo
```

Expected: both `BUILD SUCCESS`. The full test count for `graphql/spring-demo` should be: 3 (`ProductCatalogServiceTest`) + 3 (`ReviewServiceTest`) + 1 (`FailureSimulatorTest`) + 2 (`DemoExceptionResolverTest`) + 8 (`DemoControllerTest`) + 7 (`DemoIntegrationTest`) + 1 (`GraphQlSpringDemoApplicationTest`) = 25 tests, all passing.

- [ ] **Step 7: Manual smoke test**

```bash
mvn -pl graphql/spring-demo spring-boot:run
```

In another terminal, run the four `curl` walkthrough examples from `spring-demo/README.md` (Step 3 above) and confirm each returns the expected shape; open http://localhost:8092/graphiql and run the subscription example, then trigger the matching mutation from a `curl` call and confirm the subscription tab receives it live. Stop the app (`Ctrl+C`) once confirmed.

- [ ] **Step 8: Commit**

```bash
git add communication-protocols/README.md communication-protocols/graphql/README.md communication-protocols/graphql/spring-demo/README.md CLAUDE.md
git commit -m "docs(communication-protocols): document the GraphQL demo"
```
