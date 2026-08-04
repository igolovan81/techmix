# GraphQL Query Depth & Complexity Limiting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add query depth limiting and complexity (cost) limiting to `communication-protocols/graphql/spring-demo`, closing gap #1 in `communication-protocols/graphql/GAPS.md`.

**Architecture:** Two graphql-java `Instrumentation` beans (`MaxQueryDepthInstrumentation`, `MaxQueryComplexityInstrumentation`, both from `graphql-java` 22.3, already on the classpath transitively via `spring-boot-starter-graphql`) are configured via a new `QueryLimitsConfig`. Both are subclassed so their rejection error carries this app's `BAD_REQUEST` classification (`org.springframework.graphql.execution.ErrorType.BAD_REQUEST`) instead of graphql-java's own `ExecutionAborted`. Complexity uses a custom `FieldComplexityCalculator` that weights connection fields (`products`, `Category.children`, `Category.products`, `Product.reviews`, `User.orders`, `orders`) by their `first` argument instead of the library's flat per-field default.

**Tech Stack:** Java 21, Spring Boot 3.4.4, Spring for GraphQL, graphql-java 22.3, JUnit 5, AssertJ, Spring GraphQL Test (`HttpGraphQlTester`).

## Global Constraints

- No new Maven dependencies — graphql-java 22.3 is already transitively present via `spring-boot-starter-graphql`.
- Follow this module's existing conventions exactly: `@ConfigurationProperties` record style (`config/SeedProperties.java`), `ApplicationContextRunner`-based config wiring tests (`config/CacheConfigTest.java`), `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `HttpGraphQlTester` integration style (`controller/DemoIntegrationTest.java`).
- Rejection errors must classify as `org.springframework.graphql.execution.ErrorType.BAD_REQUEST` — the same convention `exception/DemoExceptionResolver.java` uses for other validation failures. Do not modify `DemoExceptionResolver` itself; this feature uses a parallel mechanism at the instrumentation layer (rejection happens before any data fetcher runs, so `DemoExceptionResolver`, which only resolves data-fetcher exceptions, is never reached).
- `app.graphql.max-query-depth: 15` and `app.graphql.max-query-complexity: 10000` are the exact starting values — empirically measured during planning against this project's real schema and query shapes (see each task's rationale below), not guessed. If any step's "Expected" output doesn't match because the schema has changed since this plan was written, trust the measurement over the number in this document and adjust the two properties accordingly — do not weaken a test to force a stale number to pass.
- `communication-protocols/graphql/README.md` and `communication-protocols/graphql/GAPS.md` are the only docs to update (per the approved design spec) — do not touch `spring-demo/README.md`.

---

### Task 1: `QueryLimitsProperties` configuration record

**Files:**
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/config/QueryLimitsProperties.java`
- Modify: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/GraphQlSpringDemoApplication.java`
- Modify: `communication-protocols/graphql/spring-demo/src/main/resources/application.yml`
- Modify: `communication-protocols/graphql/spring-demo/src/test/resources/application.yml`
- Test: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/config/QueryLimitsPropertiesTest.java`

**Interfaces:**
- Produces: `public record QueryLimitsProperties(int maxQueryDepth, int maxQueryComplexity)`, prefix `app.graphql`, consumed by Task 4's `QueryLimitsConfig` and Task 5's `QueryLimitsTest`.

- [ ] **Step 1: Write the failing test**

Create `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/config/QueryLimitsPropertiesTest.java`:

```java
package com.testingai.graphql.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class QueryLimitsPropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(TestConfig.class);

	@Test
	void bindsMaxQueryDepthAndMaxQueryComplexity_fromAppGraphqlProperties() {
		contextRunner
				.withPropertyValues("app.graphql.max-query-depth=20", "app.graphql.max-query-complexity=12345")
				.run(context -> {
					QueryLimitsProperties properties = context.getBean(QueryLimitsProperties.class);
					assertThat(properties.maxQueryDepth()).isEqualTo(20);
					assertThat(properties.maxQueryComplexity()).isEqualTo(12345);
				});
	}

	@EnableConfigurationProperties(QueryLimitsProperties.class)
	static class TestConfig {
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (from `communication-protocols/graphql/spring-demo`): `mvn test -Dtest=QueryLimitsPropertiesTest`
Expected: FAIL — compile error, `QueryLimitsProperties` doesn't exist yet.

- [ ] **Step 3: Write minimal implementation**

Create `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/config/QueryLimitsProperties.java`:

```java
package com.testingai.graphql.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.graphql")
public record QueryLimitsProperties(int maxQueryDepth, int maxQueryComplexity) {
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=QueryLimitsPropertiesTest`
Expected: PASS

- [ ] **Step 5: Wire the property into the real application**

In `GraphQlSpringDemoApplication.java`, change:

```java
import com.testingai.graphql.config.SeedProperties;
...
@EnableConfigurationProperties(SeedProperties.class)
```

to:

```java
import com.testingai.graphql.config.QueryLimitsProperties;
import com.testingai.graphql.config.SeedProperties;
...
@EnableConfigurationProperties({SeedProperties.class, QueryLimitsProperties.class})
```

In `src/main/resources/application.yml`, add a `graphql:` block as a sibling of the existing `seed:` block under `app:` (do not confuse with the pre-existing `spring.graphql.*` block earlier in the file — this is a different, `app.graphql.*` path):

```yaml
app:
  seed:
    enabled: true
    user-count: 100
    category-count: 100
    product-count: 10000
    min-reviews-per-product: 3
    max-reviews-per-product: 10
    order-count: 3000
  graphql:
    max-query-depth: 15
    max-query-complexity: 10000
```

In `src/test/resources/application.yml`, add the identical block (the test classpath's `application.yml` fully replaces the main one rather than merging with it, so without this the property would bind to `0`, which would reject every query in every existing test):

```yaml
app:
  seed:
    enabled: true
    user-count: 10
    category-count: 10
    product-count: 50
    min-reviews-per-product: 1
    max-reviews-per-product: 3
    order-count: 20
  graphql:
    max-query-depth: 15
    max-query-complexity: 10000
```

`15` and `10000` are empirically measured, not arbitrary: against this project's real `schema.graphqls`, the standard GraphiQL introspection query measures depth 13 and complexity 62; a representative multi-field legitimate query (`products` with nested `categories`, `category` with `children`/`products`, `product` with `reviews`/`author`) measures depth 6, complexity 311; and a query using the maximum legitimate page size at two nested connection levels (`first: 50` twice, matching `CursorPagination.MAX_FIRST`) measures complexity 7,651 — all comfortably under 15 / 10000, while a query nesting the `Product → Review → User → Order → Product` cycle twice measures depth 22 (rejected) and a `first: 1000` two-level nested query measures complexity 3,003,001 (rejected). Task 5 exercises all of these directly.

- [ ] **Step 6: Run the full existing test suite to confirm nothing else broke**

Run: `mvn test`
Expected: PASS (all existing tests unaffected — no instrumentation is registered yet, this task only adds the property).

- [ ] **Step 7: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/config/QueryLimitsProperties.java communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/GraphQlSpringDemoApplication.java communication-protocols/graphql/spring-demo/src/main/resources/application.yml communication-protocols/graphql/spring-demo/src/test/resources/application.yml communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/config/QueryLimitsPropertiesTest.java
git commit -m "feat(communication-protocols): add app.graphql.max-query-depth/complexity properties"
```

---

### Task 2: `PaginationAwareFieldComplexityCalculator`

**Files:**
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/instrumentation/PaginationAwareFieldComplexityCalculator.java`
- Test: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/instrumentation/PaginationAwareFieldComplexityCalculatorTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `public class PaginationAwareFieldComplexityCalculator implements graphql.analysis.FieldComplexityCalculator` with `public int calculate(FieldComplexityEnvironment environment, int childComplexity)` — consumed by Task 4's `QueryLimitsConfig`.

This calculator only multiplies a field's cost by its `first` argument when the field's **schema definition** actually declares a `first` argument (checked via `environment.getFieldDefinition().getArgument("first") != null`) — not merely whether `first` happens to be present in the supplied arguments map. This distinction matters: plain object fields like `Review.author` or `Category.parent` have no `first` argument at all, so they must fall back to the library's ordinary flat `1 + childComplexity`, not be multiplied by the 10-item default.

- [ ] **Step 1: Write the failing test**

Create `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/instrumentation/PaginationAwareFieldComplexityCalculatorTest.java`:

```java
package com.testingai.graphql.instrumentation;

import graphql.Scalars;
import graphql.analysis.FieldComplexityEnvironment;
import graphql.language.Field;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationAwareFieldComplexityCalculatorTest {

	private final PaginationAwareFieldComplexityCalculator calculator = new PaginationAwareFieldComplexityCalculator();

	@Test
	void multipliesChildComplexityByFirstArgument_whenFieldDeclaresFirstArgument() {
		FieldComplexityEnvironment environment = paginatedFieldEnvironment(Map.of("first", 40));

		int complexity = calculator.calculate(environment, 10);

		assertThat(complexity).isEqualTo(1 + 40 * 10);
	}

	@Test
	void usesDefaultMultiplierOfTen_whenFirstArgumentDeclaredButOmitted() {
		FieldComplexityEnvironment environment = paginatedFieldEnvironment(Map.of());

		int complexity = calculator.calculate(environment, 3);

		assertThat(complexity).isEqualTo(1 + 10 * 3);
	}

	@Test
	void doesNotMultiply_whenFieldHasNoFirstArgumentInItsSchemaDefinition() {
		GraphQLFieldDefinition fieldDefinition = GraphQLFieldDefinition.newFieldDefinition().name("author")
				.type(Scalars.GraphQLID).build();
		FieldComplexityEnvironment environment = new FieldComplexityEnvironment(Field.newField("author").build(),
				fieldDefinition, GraphQLObjectType.newObject().name("Query").build(), Map.of(), null);

		int complexity = calculator.calculate(environment, 7);

		assertThat(complexity).isEqualTo(1 + 7);
	}

	private FieldComplexityEnvironment paginatedFieldEnvironment(Map<String, Object> arguments) {
		GraphQLFieldDefinition fieldDefinition = GraphQLFieldDefinition.newFieldDefinition().name("products")
				.type(GraphQLList.list(Scalars.GraphQLID))
				.argument(GraphQLArgument.newArgument().name("first").type(Scalars.GraphQLInt)).build();
		return new FieldComplexityEnvironment(Field.newField("products").build(), fieldDefinition,
				GraphQLObjectType.newObject().name("Query").build(), arguments, null);
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=PaginationAwareFieldComplexityCalculatorTest`
Expected: FAIL — compile error, `PaginationAwareFieldComplexityCalculator` doesn't exist yet.

- [ ] **Step 3: Write minimal implementation**

Create `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/instrumentation/PaginationAwareFieldComplexityCalculator.java`:

```java
package com.testingai.graphql.instrumentation;

import graphql.analysis.FieldComplexityCalculator;
import graphql.analysis.FieldComplexityEnvironment;

/**
 * Weights a field's cost by its {@code first} argument instead of graphql-java's default flat
 * {@code 1 + childComplexity}, but only for fields whose schema definition actually declares {@code first} —
 * plain object fields like {@code Review.author} or {@code Category.parent} keep the flat cost. When a
 * paginated field's {@code first} argument is omitted by the client, this defaults the multiplier to 10,
 * mirroring {@code CursorPagination.DEFAULT_FIRST} — the actual page size the server would apply.
 */
public class PaginationAwareFieldComplexityCalculator implements FieldComplexityCalculator {

	private static final int DEFAULT_FIRST = 10;

	@Override
	public int calculate(FieldComplexityEnvironment environment, int childComplexity) {
		boolean isPaginated = environment.getFieldDefinition().getArgument("first") != null;
		if (!isPaginated) {
			return 1 + childComplexity;
		}
		Object first = environment.getArguments().get("first");
		int multiplier = first instanceof Integer value ? value : DEFAULT_FIRST;
		return 1 + multiplier * childComplexity;
	}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=PaginationAwareFieldComplexityCalculatorTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/instrumentation/PaginationAwareFieldComplexityCalculator.java communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/instrumentation/PaginationAwareFieldComplexityCalculatorTest.java
git commit -m "feat(communication-protocols): add pagination-aware GraphQL field complexity calculator"
```

---

### Task 3: `BadRequestMaxQueryDepthInstrumentation` and `BadRequestMaxQueryComplexityInstrumentation`

**Files:**
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/instrumentation/BadRequestMaxQueryDepthInstrumentation.java`
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/instrumentation/BadRequestMaxQueryComplexityInstrumentation.java`
- Test: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/instrumentation/BadRequestMaxQueryDepthInstrumentationTest.java`
- Test: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/instrumentation/BadRequestMaxQueryComplexityInstrumentationTest.java`

**Interfaces:**
- Consumes (complexity instrumentation only): `com.testingai.graphql.instrumentation.PaginationAwareFieldComplexityCalculator` from Task 2, passed as a constructor argument by whoever builds it (Task 4; these two tests build their own inline `FieldComplexityCalculator` to stay independent of Task 2).
- Produces: `public class BadRequestMaxQueryDepthInstrumentation extends graphql.analysis.MaxQueryDepthInstrumentation` (constructor `(int maxDepth)`), and `public class BadRequestMaxQueryComplexityInstrumentation extends graphql.analysis.MaxQueryComplexityInstrumentation` (constructor `(int maxComplexity, FieldComplexityCalculator fieldComplexityCalculator)`) — both `graphql.execution.instrumentation.Instrumentation` subtypes, consumed by Task 4's `QueryLimitsConfig`.

**Why a subclass at all:** `MaxQueryDepthInstrumentation`/`MaxQueryComplexityInstrumentation` reject by throwing `graphql.execution.AbortExecutionException`, whose own `getErrorType()` is hardcoded to `graphql.ErrorType.ExecutionAborted` (verified by reading the graphql-java 22.3 source) — a different classification than this app's `BAD_REQUEST` convention, and this rejection happens in `beginExecuteOperation`, before any data fetcher runs, so `DemoExceptionResolver` (which only resolves data-fetcher exceptions) never sees it. Both base classes expose a `protected AbortExecutionException mkAbortException(...)` hook specifically for overriding. `AbortExecutionException` also has an `(Collection<GraphQLError> underlyingErrors)` constructor — when given a non-empty list, graphql-java's top-level abort handler builds the `ExecutionResult` directly from that list, bypassing `getErrorType()` entirely (verified by reading `graphql.GraphQL`'s abort-handling code). So each override hands back one `GraphQLError` built with Spring's `ErrorType.BAD_REQUEST`.

- [ ] **Step 1: Write the failing tests**

Create `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/instrumentation/BadRequestMaxQueryDepthInstrumentationTest.java`:

```java
package com.testingai.graphql.instrumentation;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.execution.ErrorType;

import static org.assertj.core.api.Assertions.assertThat;

class BadRequestMaxQueryDepthInstrumentationTest {

	private static final String SDL = """
			type Query { a: A }
			type A { b: A id: ID }
			""";

	@Test
	void rejectsWithBadRequestClassification_whenDepthExceedsMax() {
		GraphQL graphQl = GraphQL.newGraphQL(buildSchema())
				.instrumentation(new BadRequestMaxQueryDepthInstrumentation(1)).build();

		ExecutionResult result = graphQl.execute("{ a { b { id } } }");

		assertThat(result.getErrors()).hasSize(1);
		GraphQLError error = result.getErrors().get(0);
		assertThat(error.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		assertThat(error.getMessage()).isEqualTo("Query depth 3 exceeds maximum allowed depth 1");
	}

	@Test
	void allowsExecution_whenDepthWithinMax() {
		GraphQL graphQl = GraphQL.newGraphQL(buildSchema())
				.instrumentation(new BadRequestMaxQueryDepthInstrumentation(10)).build();

		ExecutionResult result = graphQl.execute("{ a { b { id } } }");

		assertThat(result.getErrors()).isEmpty();
	}

	private GraphQLSchema buildSchema() {
		TypeDefinitionRegistry registry = new SchemaParser().parse(SDL);
		return new SchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.newRuntimeWiring().build());
	}
}
```

`"Query depth 3 exceeds maximum allowed depth 1"` is not a guess: `{ a { b { id } } }` against this two-type schema was measured at depth 3 using graphql-java's own `MaxQueryDepthInstrumentation` during planning.

Create `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/instrumentation/BadRequestMaxQueryComplexityInstrumentationTest.java`:

```java
package com.testingai.graphql.instrumentation;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.analysis.FieldComplexityCalculator;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.execution.ErrorType;

import static org.assertj.core.api.Assertions.assertThat;

class BadRequestMaxQueryComplexityInstrumentationTest {

	private static final String SDL = """
			type Query { products(first: Int): ProductConnection }
			type ProductConnection { edges: [ProductEdge] }
			type ProductEdge { node: Product }
			type Product { id: ID }
			""";

	private static final String QUERY = "{ products(first: 5) { edges { node { id } } } }";

	@Test
	void rejectsWithBadRequestClassification_whenComplexityExceedsMax() {
		GraphQL graphQl = GraphQL.newGraphQL(buildSchema())
				.instrumentation(new BadRequestMaxQueryComplexityInstrumentation(15, calculator())).build();

		ExecutionResult result = graphQl.execute(QUERY);

		assertThat(result.getErrors()).hasSize(1);
		GraphQLError error = result.getErrors().get(0);
		assertThat(error.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		assertThat(error.getMessage()).isEqualTo("Query complexity 16 exceeds maximum allowed complexity 15");
	}

	@Test
	void allowsExecution_whenComplexityWithinMax() {
		GraphQL graphQl = GraphQL.newGraphQL(buildSchema())
				.instrumentation(new BadRequestMaxQueryComplexityInstrumentation(20, calculator())).build();

		ExecutionResult result = graphQl.execute(QUERY);

		assertThat(result.getErrors()).isEmpty();
	}

	private FieldComplexityCalculator calculator() {
		return new PaginationAwareFieldComplexityCalculator();
	}

	private GraphQLSchema buildSchema() {
		TypeDefinitionRegistry registry = new SchemaParser().parse(SDL);
		return new SchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.newRuntimeWiring().build());
	}
}
```

`"Query complexity 16 exceeds maximum allowed complexity 15"` is likewise measured, not guessed: `{ products(first: 5) { edges { node { id } } } }` against this schema, using `PaginationAwareFieldComplexityCalculator`, computes to exactly 16 (`id`=1, `node`=1+1=2, `edges`=1+2=3, `products` has `first`, multiplier 5 → `1 + 5*3 = 16`).

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=BadRequestMaxQueryDepthInstrumentationTest,BadRequestMaxQueryComplexityInstrumentationTest`
Expected: FAIL — compile errors, neither class exists yet.

- [ ] **Step 3: Write minimal implementation**

Create `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/instrumentation/BadRequestMaxQueryDepthInstrumentation.java`:

```java
package com.testingai.graphql.instrumentation;

import graphql.GraphqlErrorBuilder;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.AbortExecutionException;
import org.springframework.graphql.execution.ErrorType;

import java.util.List;

/**
 * {@link MaxQueryDepthInstrumentation} rejects via {@link AbortExecutionException}, whose {@code getErrorType()}
 * is hardcoded to {@code graphql.ErrorType.ExecutionAborted} — a different classification than this app's
 * {@code BAD_REQUEST} convention ({@link com.testingai.graphql.exception.DemoExceptionResolver}). Overriding
 * {@code mkAbortException} to carry a pre-built {@link graphql.GraphQLError} makes graphql-java's abort handler
 * use that error's classification verbatim instead, bypassing {@code ExecutionAborted} entirely.
 */
public class BadRequestMaxQueryDepthInstrumentation extends MaxQueryDepthInstrumentation {

	public BadRequestMaxQueryDepthInstrumentation(int maxDepth) {
		super(maxDepth);
	}

	@Override
	protected AbortExecutionException mkAbortException(int depth, int maxDepth) {
		return new AbortExecutionException(List.of(GraphqlErrorBuilder.newError().errorType(ErrorType.BAD_REQUEST)
				.message("Query depth " + depth + " exceeds maximum allowed depth " + maxDepth).build()));
	}
}
```

Create `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/instrumentation/BadRequestMaxQueryComplexityInstrumentation.java`:

```java
package com.testingai.graphql.instrumentation;

import graphql.GraphqlErrorBuilder;
import graphql.analysis.FieldComplexityCalculator;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.execution.AbortExecutionException;
import org.springframework.graphql.execution.ErrorType;

import java.util.List;

/**
 * Same {@code AbortExecutionException} remapping as {@link BadRequestMaxQueryDepthInstrumentation}, for the
 * complexity limit.
 */
public class BadRequestMaxQueryComplexityInstrumentation extends MaxQueryComplexityInstrumentation {

	public BadRequestMaxQueryComplexityInstrumentation(int maxComplexity,
			FieldComplexityCalculator fieldComplexityCalculator) {
		super(maxComplexity, fieldComplexityCalculator);
	}

	@Override
	protected AbortExecutionException mkAbortException(int totalComplexity, int maxComplexity) {
		return new AbortExecutionException(List.of(GraphqlErrorBuilder.newError().errorType(ErrorType.BAD_REQUEST)
				.message("Query complexity " + totalComplexity + " exceeds maximum allowed complexity " + maxComplexity)
				.build()));
	}
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=BadRequestMaxQueryDepthInstrumentationTest,BadRequestMaxQueryComplexityInstrumentationTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/instrumentation/BadRequestMaxQueryDepthInstrumentation.java communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/instrumentation/BadRequestMaxQueryComplexityInstrumentation.java communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/instrumentation/BadRequestMaxQueryDepthInstrumentationTest.java communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/instrumentation/BadRequestMaxQueryComplexityInstrumentationTest.java
git commit -m "feat(communication-protocols): classify GraphQL depth/complexity rejections as BAD_REQUEST"
```

---

### Task 4: `QueryLimitsConfig` wiring

**Files:**
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/config/QueryLimitsConfig.java`
- Test: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/config/QueryLimitsConfigTest.java`

**Interfaces:**
- Consumes: `QueryLimitsProperties` (Task 1), `PaginationAwareFieldComplexityCalculator` (Task 2), `BadRequestMaxQueryDepthInstrumentation`/`BadRequestMaxQueryComplexityInstrumentation` (Task 3).
- Produces: two `graphql.execution.instrumentation.Instrumentation` beans, auto-collected by Spring Boot's `GraphQlAutoConfiguration` (confirmed by reading `spring-boot-autoconfigure` 3.4.4 — it gathers every `Instrumentation` bean via `ObjectProvider<Instrumentation>` with no additional wiring needed).

- [ ] **Step 1: Write the failing test**

Create `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/config/QueryLimitsConfigTest.java`, following `CacheConfigTest`'s `ApplicationContextRunner` style:

```java
package com.testingai.graphql.config;

import com.testingai.graphql.instrumentation.BadRequestMaxQueryComplexityInstrumentation;
import com.testingai.graphql.instrumentation.BadRequestMaxQueryDepthInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class QueryLimitsConfigTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(QueryLimitsConfig.class)
			.withBean(QueryLimitsProperties.class, () -> new QueryLimitsProperties(15, 10000));

	@Test
	void registersBothInstrumentationsWithBadRequestClassification() {
		contextRunner.run(context -> {
			assertThat(context.getBeansOfType(Instrumentation.class).values()).hasSize(2)
					.hasAtLeastOneElementOfType(BadRequestMaxQueryDepthInstrumentation.class)
					.hasAtLeastOneElementOfType(BadRequestMaxQueryComplexityInstrumentation.class);
		});
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=QueryLimitsConfigTest`
Expected: FAIL — compile error, `QueryLimitsConfig` doesn't exist yet.

- [ ] **Step 3: Write minimal implementation**

Create `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/config/QueryLimitsConfig.java`:

```java
package com.testingai.graphql.config;

import com.testingai.graphql.instrumentation.BadRequestMaxQueryComplexityInstrumentation;
import com.testingai.graphql.instrumentation.BadRequestMaxQueryDepthInstrumentation;
import com.testingai.graphql.instrumentation.PaginationAwareFieldComplexityCalculator;
import graphql.execution.instrumentation.Instrumentation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueryLimitsConfig {

	@Bean
	public Instrumentation maxQueryDepthInstrumentation(QueryLimitsProperties properties) {
		return new BadRequestMaxQueryDepthInstrumentation(properties.maxQueryDepth());
	}

	@Bean
	public Instrumentation maxQueryComplexityInstrumentation(QueryLimitsProperties properties) {
		return new BadRequestMaxQueryComplexityInstrumentation(properties.maxQueryComplexity(),
				new PaginationAwareFieldComplexityCalculator());
	}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=QueryLimitsConfigTest`
Expected: PASS

- [ ] **Step 5: Run the full existing test suite**

Run: `mvn test`
Expected: PASS — `QueryLimitsConfig` is now a real `@Configuration` on the app's component-scanned package, so `@SpringBootTest`-based tests (`DemoIntegrationTest`, `CategoryServiceCachingTest`, etc.) now execute with both instrumentations actually active. Every query those tests issue is well within the 15/10000 budget (the deepest, `product { reviews { author } }`-shaped queries, measure at depth ≤6 and complexity in the low hundreds — see Task 1's measurements), so no existing test should be affected. If anything unexpectedly fails here, stop and investigate before continuing — do not proceed to Task 5 with a red suite.

- [ ] **Step 6: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/config/QueryLimitsConfig.java communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/config/QueryLimitsConfigTest.java
git commit -m "feat(communication-protocols): wire GraphQL query depth/complexity instrumentation beans"
```

---

### Task 5: End-to-end verification against the real app and schema

**Files:**
- Create: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/QueryLimitsTest.java`

**Interfaces:**
- Consumes: `QueryLimitsProperties` (Task 1, autowired to read the configured `maxQueryDepth` for the self-calibrating boundary test), the real running app wired by Tasks 1–4 (via `@SpringBootTest`).

This task doesn't drive new production code (Tasks 1–4 already built the full feature) — it's a characterization/verification suite proving the real, fully-wired app behaves as designed against its real schema and real HTTP/GraphQL stack, the same role `DemoIntegrationTest` plays for the other five patterns. There's no red-green cycle here; every test should pass on the first run once written correctly.

Note on scope versus the design spec: the spec's Testing section asks for "boundary correctness" generically. This plan implements that precisely for depth (a self-referential single-field chain, `Category.parent`, makes exact nesting-level control easy — see Step 3) but only as a clear pass/fail pair for complexity (Step 5) — complexity's cost is a compound sum across a query's whole shape (sibling fields, `edges`/`node` wrapper overhead), not a clean linear function of one knob the way depth-per-nesting-level is, so hand-deriving an exact "one unit over the limit" value isn't practical. This is a deliberate refinement made during planning, not a dropped requirement.

- [ ] **Step 1: Write the regression and introspection tests**

Create `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/QueryLimitsTest.java`:

```java
package com.testingai.graphql.controller;

import com.testingai.graphql.config.QueryLimitsProperties;
import com.testingai.graphql.entity.CategoryEntity;
import com.testingai.graphql.entity.ProductEntity;
import com.testingai.graphql.repository.CategoryRepository;
import com.testingai.graphql.repository.ProductRepository;
import graphql.introspection.IntrospectionQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueryLimitsTest {

	@LocalServerPort
	private int port;

	@Autowired
	private ProductRepository productRepository;
	@Autowired
	private CategoryRepository categoryRepository;
	@Autowired
	private QueryLimitsProperties queryLimitsProperties;

	private HttpGraphQlTester graphQlTester;

	@BeforeEach
	void setUpTester() {
		WebTestClient webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port + "/graphql")
				.build();
		graphQlTester = HttpGraphQlTester.create(webTestClient);
	}

	private Long saveProduct(String name) {
		ProductEntity entity = new ProductEntity();
		entity.setName(name);
		entity.setPriceCents(1000);
		entity.setStockQty(10);
		return productRepository.save(entity).getId();
	}

	private Long saveCategory(String name) {
		CategoryEntity entity = new CategoryEntity();
		entity.setName(name);
		return categoryRepository.save(entity).getId();
	}

	@Test
	void regression_representativeLegitimateQueriesStillSucceed() {
		String tag = "tag" + System.nanoTime();
		saveProduct(tag + "-widget");
		Long categoryId = saveCategory(tag + "-category");

		graphQlTester.document("""
				query {
				  products(filter: { nameContains: "%s" }, first: 10) {
				    totalCount
				    edges { cursor node { id name priceCents stockQty categories { id name } } }
				    pageInfo { hasNextPage endCursor }
				  }
				  category(id: "%s") {
				    id name
				    children(first: 10) { edges { node { id name } } }
				    products(first: 10) { edges { node { id name } } }
				  }
				}
				""".formatted(tag, categoryId)).execute().path("products.totalCount").entity(Integer.class)
				.isEqualTo(1).path("category.id").entity(String.class).isEqualTo(categoryId.toString());
	}

	@Test
	void regression_introspectionQueryStillSucceeds() {
		graphQlTester.document(IntrospectionQuery.INTROSPECTION_QUERY).execute().path("__schema.queryType.name")
				.entity(String.class).isEqualTo("Query");
	}
}
```

`saveProduct`/`saveCategory` deliberately avoid touching `product(id)` — the only field in this schema with `FailureSimulator`'s real 5% simulated failure (verified in `DemoController.java`, `product(id)` resolver) — so these tests don't need the retry-loop pattern `DemoIntegrationTest` uses elsewhere; the query above only exercises `products`/`category`, neither of which is affected.

The regression query is a strict subset of a combined query measured during planning at depth 6 / complexity 311 — both far under the configured 15 / 10000 — so it's guaranteed to stay under budget without re-measuring it separately (removing a sibling field can't increase either metric).

- [ ] **Step 2: Run to confirm the regression tests pass**

Run: `mvn test -Dtest=QueryLimitsTest`
Expected: PASS (2 tests)

- [ ] **Step 3: Add the depth-limit tests**

Add these members to `QueryLimitsTest` (methods can go anywhere in the class body; imports above already cover everything needed):

```java
	@Test
	void depthLimit_rejectsQueryWalkingProductReviewUserOrderCycleTwice() {
		List<ResponseError> errors = new ArrayList<>();
		graphQlTester.document(cyclicDepthQuery(2)).execute().errors().satisfy(errors::addAll);

		assertThat(errors).hasSize(1);
		assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		assertThat(errors.get(0).getMessage()).contains("exceeds maximum allowed depth");
	}

	@Test
	void depthLimit_boundaryIsExactlyAtConfiguredMaxDepth() {
		Long categoryId = saveCategory("tag" + System.nanoTime() + "-category");
		int maxDepth = queryLimitsProperties.maxQueryDepth();

		// Query depth is a structural property of the query document, independent of real data — this succeeds
		// or fails purely from the query shape, even against a category with no real parent chain that deep.
		// graphql-java's own depth count for a given nesting level includes a fixed offset (the outer
		// `category(id)` field, the `query { }` wrapper, etc.) that's an internal detail rather than something
		// to hard-code here — so calibrate it from a deliberately oversized probe query's own rejection message,
		// then derive the nesting count that lands exactly on maxDepth.
		int probeNestingLevels = maxDepth + 10;
		List<ResponseError> probeErrors = new ArrayList<>();
		graphQlTester.document(categoryParentChainQuery(categoryId, probeNestingLevels)).execute().errors()
				.satisfy(probeErrors::addAll);
		assertThat(probeErrors).hasSize(1);
		int measuredDepth = extractMeasuredDepth(probeErrors.get(0).getMessage());
		int offset = measuredDepth - probeNestingLevels;

		int atLimitNestingLevels = maxDepth - offset;
		graphQlTester.document(categoryParentChainQuery(categoryId, atLimitNestingLevels)).execute()
				.path("category.id").entity(String.class).isEqualTo(categoryId.toString());

		List<ResponseError> overLimitErrors = new ArrayList<>();
		graphQlTester.document(categoryParentChainQuery(categoryId, atLimitNestingLevels + 1)).execute().errors()
				.satisfy(overLimitErrors::addAll);
		assertThat(overLimitErrors).hasSize(1);
		assertThat(overLimitErrors.get(0).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
	}

	private String cyclicDepthQuery(int cycles) {
		String openCycle = "reviews(first: 1) { edges { node { author { "
				+ "orders(first: 1) { edges { node { items { product { ";
		String closeCycle = " } } } } } } } } }";

		StringBuilder query = new StringBuilder();
		query.append("query { products(first: 1) { edges { node { ");
		query.append(openCycle.repeat(cycles));
		query.append("id");
		query.append(closeCycle.repeat(cycles));
		query.append(" } } } }");
		return query.toString();
	}

	private String categoryParentChainQuery(Long categoryId, int nestingLevels) {
		StringBuilder query = new StringBuilder("query { category(id: \"").append(categoryId).append("\") { id ");
		query.append("parent { ".repeat(nestingLevels));
		query.append("id");
		query.append(" }".repeat(nestingLevels));
		query.append(" } }");
		return query.toString();
	}

	private int extractMeasuredDepth(String message) {
		Matcher matcher = Pattern.compile("Query depth (\\d+) exceeds maximum allowed depth").matcher(message);
		assertThat(matcher.find()).as("unexpected rejection message: " + message).isTrue();
		return Integer.parseInt(matcher.group(1));
	}
```

`cyclicDepthQuery(2)` builds (not hand-nests as a literal string — brace-matching a 9-level-per-cycle nested query by hand is exactly the kind of thing that's easy to get subtly wrong) two full turns of the `Product → reviews → author → orders → items → product` cycle called out in the design spec and `GAPS.md`. Measured at depth 22 during planning against the real schema — comfortably over the configured 15, so this doesn't depend on the self-calibration trick the boundary test uses; it's deliberately, obviously over budget. It needs no fixture data — depth rejection happens before any resolver runs, so an empty `products` table rejects identically to a full one.

- [ ] **Step 4: Run to confirm the depth tests pass**

Run: `mvn test -Dtest=QueryLimitsTest`
Expected: PASS (4 tests)

- [ ] **Step 5: Add the complexity-limit test**

Add this member to `QueryLimitsTest`:

```java
	@Test
	void complexityLimit_rejectsExtremelyWideNestedConnectionQuery() {
		String query = """
				query {
				  products(first: 1000) {
				    edges { node {
				      reviews(first: 1000) { edges { node { id } } }
				    } }
				  }
				}
				""";

		List<ResponseError> errors = new ArrayList<>();
		graphQlTester.document(query).execute().errors().satisfy(errors::addAll);

		assertThat(errors).hasSize(1);
		assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		assertThat(errors.get(0).getMessage()).contains("exceeds maximum allowed complexity");
	}
```

Measured at complexity 3,003,001 during planning — nesting two `first: 1000` connection levels, not something larger like `first: 100000`, deliberately: at that scale the multiplication in `PaginationAwareFieldComplexityCalculator` overflows a 32-bit `int` and can wrap around to a value that no longer exceeds the budget, silently defeating the check (confirmed during planning — this is a real edge case, not a hypothetical one). `first: 1000` twice stays well within `int` range while still landing 300x over the configured budget.

- [ ] **Step 6: Run the full test class**

Run: `mvn test -Dtest=QueryLimitsTest`
Expected: PASS (5 tests)

- [ ] **Step 7: Run the full existing test suite one more time**

Run: `mvn test`
Expected: PASS — final confirmation that the new tests plus all previously-existing tests (`DemoIntegrationTest`, `CategoryServiceCachingTest`, etc.) coexist cleanly under the now-active instrumentation.

- [ ] **Step 8: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/QueryLimitsTest.java
git commit -m "test(communication-protocols): verify GraphQL query depth/complexity limiting end-to-end"
```

---

### Task 6: Documentation

**Files:**
- Modify: `communication-protocols/graphql/README.md`
- Modify: `communication-protocols/graphql/GAPS.md`

- [ ] **Step 1: Update the Scope section in `README.md`**

Find this sentence (start of the `## Scope` section):

```
No query depth/complexity limiting, no persisted queries, no GraphQL federation — this is a protocol-pattern demo, not a production-hardening guide (same spirit as the gRPC demo's "no TLS" scope limit).
```

Replace it with:

```
No persisted queries, no GraphQL federation — this is a protocol-pattern demo, not a production-hardening guide (same spirit as the gRPC demo's "no TLS" scope limit). Query depth and complexity limiting is implemented (see below), but the configured limits are demo-scale starting values verified against this app's own queries, not a rigorously tuned production budget.
```

- [ ] **Step 2: Add a new pattern subsection to `README.md`**

Find the `## Running the demo` heading (the section immediately after `## Caching`). Insert a new section immediately before it:

```markdown
## Query depth & complexity limiting

This schema has a real cycle — `Product.reviews → Review.author → User.orders → Order.items → OrderItem.product → Product.reviews → ...` — that nothing in the type system stops a client from walking indefinitely in one query. Two `graphql-java` instrumentations guard against this: `MaxQueryDepthInstrumentation` caps how deeply a query can nest, and `MaxQueryComplexityInstrumentation` caps a computed cost using a custom `FieldComplexityCalculator` that weights each connection field (`products`, `Category.children`, `Category.products`, `Product.reviews`, `User.orders`, `orders`) by its `first` argument — a `products(first: 500)` query costs proportionally more than `first: 5`, instead of graphql-java's default flat per-field count. Both limits are configurable (`app.graphql.max-query-depth`, `app.graphql.max-query-complexity`) and reject with the same `BAD_REQUEST` classification as this schema's other validation errors (e.g. `addReview` against an unknown product) — even though the rejection happens at a different point in the pipeline (before any resolver runs, rather than inside one).

**Pros**
- Bounds the cost of a single request regardless of how a client nests fields — protects against both accidental (a client recursing too eagerly) and adversarial queries
- Complexity weighting scales with the same `first` argument clients already use for pagination, so the cost model lines up with what the schema already exposes rather than introducing a separate, hidden budget
- Rejection happens before any resolver runs — a too-deep or too-wide query never reaches the database

**Cons**
- The `first`-based weighting only accounts for genuinely paginated fields; a flat, unpaginated list field (e.g. `Product.categories`) isn't weighted specially
- Fixed `application.yml` constants, not tuned per-client or per-role — a legitimate power-user query and a hostile one are judged by the same budget
- Doesn't replace rate limiting or query allow-listing — a client can still send many separate, individually-legal queries in quick succession

**Typical use cases**
- Any public or third-party-facing GraphQL endpoint, where a client's query shape isn't fully trusted
- Schemas with cyclic type graphs (common in social/e-commerce domains: user → orders → items → product → reviews → user...) where naive nesting has no natural ceiling

## Running the demo
```

- [ ] **Step 3: Rewrite `GAPS.md` with gap #1 resolved and everything renumbered**

Replace the entire contents of `communication-protocols/graphql/GAPS.md` with:

```markdown
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
```

- [ ] **Step 4: Commit**

```bash
git add communication-protocols/graphql/README.md communication-protocols/graphql/GAPS.md
git commit -m "docs(communication-protocols): document GraphQL query depth/complexity limiting, resolve GAPS #1"
```
