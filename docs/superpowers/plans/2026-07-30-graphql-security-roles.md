# GraphQL Demo — Security & Roles Addendum Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add field/operation-level authorization to `communication-protocols/graphql/spring-demo` — HTTP Basic auth with two in-memory users (`user`/USER, `admin`/ADMIN), `@PreAuthorize` on `addReview`, `reviewAdded`, and a new ADMIN-only `deleteReview` mutation, plus UNAUTHORIZED/FORBIDDEN error classification.

**Architecture:** A new `SecurityConfig` (HTTP-layer `permitAll()` on `/graphql`/`/graphiql`, real authorization enforced via Spring Security method security) sits alongside the existing `DemoController`. `DemoExceptionResolver` gains a `SecurityContextHolder`-based check to distinguish "not authenticated" from "wrong role" since both throw the same `AuthorizationDeniedException` type. `ReviewService` gains a `deleteReview` method operating on its existing in-memory store.

**Tech Stack:** Spring Boot 3.4.4, Spring Security 6.4.4 (`spring-boot-starter-security`), Spring GraphQL 1.3.4, Java 21, JUnit 5, AssertJ, Mockito, `spring-graphql-test`.

## Global Constraints

- Reuse `backend/rest-api`'s exact demo users/roles/mechanism (HTTP Basic, `user`/`userPassword`→USER, `admin`/`adminPassword`→ADMIN) — **except** passwords must carry the `{noop}` encoding prefix (`{noop}userPassword`, `{noop}adminPassword`). This is not stylistic: empirically verified that Spring Security 6.x's default `PasswordEncoderFactories.createDelegatingPasswordEncoder()` throws `IllegalArgumentException` matching an unprefixed password, so `backend/rest-api`'s exact literal pattern would never authenticate successfully.
- `deleteReview(id: ID!): Boolean` — nullable return type, not `Boolean!`. Empirically verified: a non-nullable field's null-propagation-on-denial bubbles to the operation's root and nulls out the entire `data` object (including sibling fields like a successful `addReview` in the same mutation), which would silently break the partial-failure demonstration this addendum exists to show.
- Authorization is enforced at the method layer (`@PreAuthorize` on `DemoController`), not the HTTP layer. `/graphql` and `/graphiql` are `permitAll()` in `SecurityFilterChain` — GraphQL has one HTTP endpoint for every operation, so "which URL is protected" doesn't apply; only "which field" does.
- `DemoExceptionResolver` distinguishes `UNAUTHORIZED` vs `FORBIDDEN` by inspecting `SecurityContextHolder.getContext().getAuthentication()` at classification time — **not** by exception type. Both cases throw the identical `org.springframework.security.authorization.AuthorizationDeniedException` (extends `AccessDeniedException`); there is no type-level distinction available.
- Every existing test in `DemoControllerTest`, `ReviewServiceTest`, `DemoExceptionResolverTest`, and the parts of `DemoIntegrationTest` unrelated to the newly-secured fields must keep passing unmodified in behavior (only auth headers get added where a field now requires them).
- Build/test commands (per `CLAUDE.md`): from `communication-protocols/`, `mvn test -pl graphql/spring-demo -am` runs unit tests; `mvn gatling:test -pl graphql/spring-demo` runs the load test (app must be running). Use `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home` if the shell's default `java -version` isn't 21 (this repo's Lombok/Groovy tooling breaks on newer JDKs — confirmed this session with JDK 25 present on this machine).

---

### Task 1: `SecurityConfig` — HTTP Basic auth actually authenticates

**Files:**
- Modify: `communication-protocols/graphql/spring-demo/pom.xml`
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/config/SecurityConfig.java`
- Test: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/config/SecurityConfigTest.java`

**Interfaces:**
- Produces: `SecurityConfig` — a `@Configuration` class registering an `InMemoryUserDetailsManager` bean (`user`/`{noop}userPassword`→ROLE_USER, `admin`/`{noop}adminPassword`→ROLE_ADMIN) and a `SecurityFilterChain` bean permitting `/graphql` and `/graphiql` unconditionally at the HTTP layer, requiring authentication for anything else, with HTTP Basic enabled. `@EnableMethodSecurity` is turned on here for later tasks to use.

- [ ] **Step 1: Add the security starter dependency**

In `communication-protocols/graphql/spring-demo/pom.xml`, add this dependency directly after the existing `spring-boot-starter-web` entry:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

- [ ] **Step 2: Write the failing test**

Create `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/config/SecurityConfigTest.java`:

```java
package com.testingai.graphql.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityConfigTest {

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
	void basicAuth_withCorrectCredentials_authenticatesSuccessfully() {
		HttpGraphQlTester authenticated = graphQlTester.mutate()
				.header("Authorization", basicAuthHeader("user", "userPassword")).build();

		authenticated.document("""
				query {
				  products { id }
				}
				""").execute().path("products").entityList(Object.class).hasSize(40);
	}

	@Test
	void basicAuth_withWrongPassword_returnsUnauthorizedHttpStatus() {
		WebTestClient webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port + "/graphql")
				.build();

		webTestClient.post().header("Authorization", basicAuthHeader("user", "wrongPassword"))
				.contentType(MediaType.APPLICATION_JSON).bodyValue("{\"query\":\"{ products { id } }\"}").exchange()
				.expectStatus().isUnauthorized();
	}

	private static String basicAuthHeader(String username, String password) {
		String credentials = username + ":" + password;
		return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
	}
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn test -pl graphql/spring-demo -am -Dtest=SecurityConfigTest` (from `communication-protocols/`)

Expected: FAIL. With `spring-boot-starter-security` on the classpath and no explicit `SecurityFilterChain`, Spring Boot's default auto-configuration protects **every** endpoint with a single generated user and a random logged password — `user`/`userPassword` doesn't match it, so `basicAuth_withCorrectCredentials_authenticatesSuccessfully` fails (401 instead of a product list). Note this default auto-configuration will also transiently break the rest of the module's existing tests (e.g. `DemoIntegrationTest`) that call `/graphql` without credentials — that's expected and resolves once Step 4 adds the real `SecurityConfig`; don't treat it as a separate problem to fix.

- [ ] **Step 4: Implement `SecurityConfig`**

Create `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/config/SecurityConfig.java`:

```java
package com.testingai.graphql.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Same demo users/roles as {@code backend/rest-api}'s SecurityConfig, with one deliberate difference: passwords
 * carry an explicit {@code {noop}} encoding prefix. Spring Security 6.x's default DelegatingPasswordEncoder throws
 * IllegalArgumentException matching a password with no {@code {id}} prefix (verified against backend/rest-api's
 * exact unprefixed pattern), so this is required for Basic auth to actually work here, not a stylistic choice.
 *
 * <p>{@code /graphql} and {@code /graphiql} are {@code permitAll()} at the HTTP layer — GraphQL has a single
 * endpoint for every operation, so per-operation authorization is enforced with {@code @PreAuthorize} on
 * {@link com.testingai.graphql.controller.DemoController}'s individual methods instead.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	public UserDetailsService userDetailsService() {
		UserDetails user = User.withUsername("user").password("{noop}userPassword").roles("USER").build();
		UserDetails admin = User.withUsername("admin").password("{noop}adminPassword").roles("ADMIN").build();
		return new InMemoryUserDetailsManager(user, admin);
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(auth -> auth.requestMatchers("/graphql", "/graphiql").permitAll().anyRequest()
						.authenticated())
				.httpBasic();
		return http.build();
	}
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn test -pl graphql/spring-demo -am -Dtest=SecurityConfigTest` (from `communication-protocols/`)

Expected: PASS (both tests).

- [ ] **Step 6: Run the full module test suite to confirm no regressions**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn test -pl graphql/spring-demo -am` (from `communication-protocols/`)

Expected: PASS. `DemoIntegrationTest` and every other existing test call `/graphql` anonymously, which now succeeds again because of `permitAll()`.

- [ ] **Step 7: Commit**

```bash
git add communication-protocols/graphql/spring-demo/pom.xml \
        communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/config/SecurityConfig.java \
        communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/config/SecurityConfigTest.java
git commit -m "feat(communication-protocols): add HTTP Basic auth to the GraphQL demo"
```

---

### Task 2: `ReviewService.deleteReview`

**Files:**
- Modify: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/ReviewService.java`
- Test: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/domain/ReviewServiceTest.java`

**Interfaces:**
- Consumes: nothing new — operates on `ReviewService`'s existing `reviewsByProductId` field.
- Produces: `public boolean deleteReview(String reviewId)` — removes the first review matching `reviewId` across all products, returns whether one was found and removed.

- [ ] **Step 1: Write the failing tests**

In `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/domain/ReviewServiceTest.java`, add:

```java
	@Test
	void deleteReview_removesMatchingReview_andReturnsTrue() {
		Review review = service.addReview("p1", "Jordan", 5, "Great product");

		boolean deleted = service.deleteReview(review.id());

		assertThat(deleted).isTrue();
		assertThat(service.findByProductIds(List.of("p1")).get("p1")).doesNotContain(review);
	}

	@Test
	void deleteReview_returnsFalse_whenReviewIdUnknown() {
		assertThat(service.deleteReview("unknown-id")).isFalse();
	}

	@Test
	void deleteReview_leavesOtherProductsReviews_untouched() {
		Review reviewOnP1 = service.addReview("p1", "Jordan", 5, "For p1");
		Review reviewOnP2 = service.addReview("p2", "Sam", 4, "For p2");

		service.deleteReview(reviewOnP1.id());

		assertThat(service.findByProductIds(List.of("p2")).get("p2")).contains(reviewOnP2);
	}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn test -pl graphql/spring-demo -am -Dtest=ReviewServiceTest` (from `communication-protocols/`)

Expected: FAIL with "cannot find symbol: method deleteReview" (compilation error).

- [ ] **Step 3: Implement `deleteReview`**

In `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/ReviewService.java`, add this method directly after `reviewAdded()`:

```java
	/**
	 * Removes the first review matching {@code reviewId} across every product, returning whether one was found.
	 * {@code CopyOnWriteArrayList.removeIf} is atomic per list, so this is safe under concurrent {@link #addReview}
	 * calls without any additional synchronization.
	 */
	public boolean deleteReview(String reviewId) {
		for (List<Review> reviews : reviewsByProductId.values()) {
			if (reviews.removeIf(review -> review.id().equals(reviewId))) {
				return true;
			}
		}
		return false;
	}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn test -pl graphql/spring-demo -am -Dtest=ReviewServiceTest` (from `communication-protocols/`)

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/ReviewService.java \
        communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/domain/ReviewServiceTest.java
git commit -m "feat(communication-protocols): add ReviewService.deleteReview"
```

---

### Task 3: `DemoExceptionResolver` — UNAUTHORIZED vs FORBIDDEN classification

**Files:**
- Modify: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/exception/DemoExceptionResolver.java`
- Test: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/exception/DemoExceptionResolverTest.java`

**Interfaces:**
- Consumes: `org.springframework.security.access.AccessDeniedException` (the common superclass of `AuthorizationDeniedException`, which is what `@PreAuthorize` actually throws — verified against the `spring-security-core` jar).
- Produces: `resolveToSingleError` now also maps `AccessDeniedException` to `ErrorType.UNAUTHORIZED` (no real `Authentication`, or an `AnonymousAuthenticationToken`) or `ErrorType.FORBIDDEN` (a genuinely authenticated principal missing the required role), by inspecting `SecurityContextHolder.getContext().getAuthentication()`.

- [ ] **Step 1: Write the failing tests**

In `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/exception/DemoExceptionResolverTest.java`, add these imports:

```java
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
```

(`java.util.List` may already be imported — keep only one copy.)

Add these test methods:

```java
	@Test
	void resolveException_classifiesAccessDeniedAsUnauthorized_whenNoAuthenticationPresent() {
		SecurityContextHolder.clearContext();
		DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);

		List<GraphQLError> errors = resolver.resolveException(new AuthorizationDeniedException("Access Denied"), env)
				.block();

		assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
	}

	@Test
	void resolveException_classifiesAccessDeniedAsUnauthorized_whenAnonymous() {
		SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
				List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
		try {
			DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);

			List<GraphQLError> errors = resolver
					.resolveException(new AuthorizationDeniedException("Access Denied"), env).block();

			assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	@Test
	void resolveException_classifiesAccessDeniedAsForbidden_whenAuthenticatedWithoutRequiredRole() {
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken("user", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
		try {
			DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);

			List<GraphQLError> errors = resolver
					.resolveException(new AuthorizationDeniedException("Access Denied"), env).block();

			assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
		} finally {
			SecurityContextHolder.clearContext();
		}
	}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn test -pl graphql/spring-demo -am -Dtest=DemoExceptionResolverTest` (from `communication-protocols/`)

Expected: FAIL. All three new tests currently get `INTERNAL_ERROR` since `AccessDeniedException` isn't classified yet.

- [ ] **Step 3: Implement the classification**

In `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/exception/DemoExceptionResolver.java`, replace the full contents with:

```java
package com.testingai.graphql.exception;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Classifies exceptions thrown from data fetchers into typed GraphQL errors instead of leaking a raw stack trace:
 * {@link IllegalArgumentException} (e.g. {@code addReview} against an unknown product) becomes {@code BAD_REQUEST};
 * {@link AccessDeniedException} (thrown by {@code @PreAuthorize} — always as its subtype
 * {@code AuthorizationDeniedException}, for both "not authenticated" and "wrong role", verified against the
 * spring-security-core jar) becomes {@code UNAUTHORIZED} or {@code FORBIDDEN} depending on whether the current
 * {@link SecurityContextHolder} authentication represents a real, logged-in principal; anything else — including
 * {@link com.testingai.graphql.util.FailureSimulator}'s simulated failures — becomes {@code INTERNAL_ERROR}.
 */
@Component
public class DemoExceptionResolver extends DataFetcherExceptionResolverAdapter {

	@Override
	protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
		return GraphqlErrorBuilder.newError().errorType(classify(ex)).message(ex.getMessage()).build();
	}

	private ErrorType classify(Throwable ex) {
		if (ex instanceof IllegalArgumentException) {
			return ErrorType.BAD_REQUEST;
		}
		if (ex instanceof AccessDeniedException) {
			return isAnonymous() ? ErrorType.UNAUTHORIZED : ErrorType.FORBIDDEN;
		}
		return ErrorType.INTERNAL_ERROR;
	}

	private boolean isAnonymous() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication == null || authentication instanceof AnonymousAuthenticationToken;
	}
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn test -pl graphql/spring-demo -am -Dtest=DemoExceptionResolverTest` (from `communication-protocols/`)

Expected: PASS (all 5 tests — 2 pre-existing + 3 new).

- [ ] **Step 5: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/exception/DemoExceptionResolver.java \
        communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/exception/DemoExceptionResolverTest.java
git commit -m "feat(communication-protocols): classify AccessDeniedException as UNAUTHORIZED/FORBIDDEN"
```

---

### Task 4: Schema + `DemoController` wiring

**Files:**
- Modify: `communication-protocols/graphql/spring-demo/src/main/resources/graphql/schema.graphqls`
- Modify: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/controller/DemoController.java`
- Modify: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/DemoControllerTest.java`

**Interfaces:**
- Consumes: `ReviewService.deleteReview(String)` (Task 2).
- Produces: `DemoController.deleteReview(String id): boolean`, `@PreAuthorize("hasRole('ADMIN')")`. `addReview` and `reviewAdded` gain `@PreAuthorize("isAuthenticated()")`.

- [ ] **Step 1: Add the schema field**

In `communication-protocols/graphql/spring-demo/src/main/resources/graphql/schema.graphqls`, change:

```graphql
type Mutation {
    addReview(input: AddReviewInput!): Review!
}
```

to:

```graphql
type Mutation {
    addReview(input: AddReviewInput!): Review!
    deleteReview(id: ID!): Boolean
}
```

(Nullable `Boolean`, not `Boolean!` — see Global Constraints.)

- [ ] **Step 2: Write the failing `DemoControllerTest` tests**

In `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/DemoControllerTest.java`, add:

```java
	@Test
	void deleteReview_removesReview_andReturnsTrue() {
		Review review = controller.addReview(new AddReviewInput("p1", "Jordan", 5, "Great product"));

		boolean deleted = controller.deleteReview(review.id());

		assertThat(deleted).isTrue();
		assertThat(reviewService.findByProductIds(List.of("p1")).get("p1")).doesNotContain(review);
	}

	@Test
	void deleteReview_returnsFalse_whenReviewUnknown() {
		assertThat(controller.deleteReview("unknown-id")).isFalse();
	}
```

(`DemoControllerTest` calls `DemoController`'s methods directly on a plain Java object with no Spring AOP proxy, so `@PreAuthorize` has no effect here — same as it already does for `products`/`addReview`'s existing tests. Authorization enforcement itself is verified through `DemoIntegrationTest` in Task 5.)

- [ ] **Step 3: Run the tests to verify they fail**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn test -pl graphql/spring-demo -am -Dtest=DemoControllerTest` (from `communication-protocols/`)

Expected: FAIL with "cannot find symbol: method deleteReview" (compilation error).

- [ ] **Step 4: Implement the controller changes**

In `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/controller/DemoController.java`, add this import after the `SubscriptionMapping` import:

```java
import org.springframework.security.access.prepost.PreAuthorize;
```

Change the `addReview` method signature to add `@PreAuthorize`:

```java
	@MutationMapping
	@PreAuthorize("isAuthenticated()")
	public Review addReview(@Argument AddReviewInput input) {
```

Change the `reviewAdded` method signature to add `@PreAuthorize`:

```java
	@SubscriptionMapping
	@PreAuthorize("isAuthenticated()")
	public Flux<Review> reviewAdded(@Argument String productId) {
```

Add the new mutation directly before `reviewAdded`:

```java
	/**
	 * Mutation — ADMIN-only. The one action where USER and ADMIN behave differently; every other operation in this
	 * demo either requires no role or just "logged in."
	 */
	@MutationMapping
	@PreAuthorize("hasRole('ADMIN')")
	public boolean deleteReview(@Argument String id) {
		log.info("[deleteReview] reviewId={}", id);
		return reviewService.deleteReview(id);
	}

```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn test -pl graphql/spring-demo -am -Dtest=DemoControllerTest` (from `communication-protocols/`)

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/main/resources/graphql/schema.graphqls \
        communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/controller/DemoController.java \
        communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/DemoControllerTest.java
git commit -m "feat(communication-protocols): add deleteReview mutation and wire @PreAuthorize"
```

---

### Task 5: `DemoIntegrationTest` — full authorization matrix

**Files:**
- Modify: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/DemoIntegrationTest.java`

**Interfaces:**
- Consumes: `HttpGraphQlTester.Builder.header(String, String...)`, `WebSocketGraphQlTester.Builder.header(String, String...)` (both verified against the `spring-graphql-test` 1.3.4 jar — inherited from `WebGraphQlTester.Builder`).
- Produces: nothing new consumed elsewhere — this is the end-to-end proof for the whole feature.

Empirically verified response shapes driving this task's assertions (live probe, reverted):
- Anonymous `deleteReview` → exactly **one** error, `UNAUTHORIZED`, `data: { deleteReview: null }`.
- USER `deleteReview` → exactly **one** error, `FORBIDDEN`, `data: { deleteReview: null }`.
- ADMIN `deleteReview` → no error, `data: { deleteReview: false }` for an unknown id.
- Anonymous `addReview` → **two** errors (our `UNAUTHORIZED` classification **and** graphql-java's own `NullValueInNonNullableField` error, because `Review!` is non-nullable), `data: null` for the whole operation. This mirrors the existing `mutation_addReview_isRejected_whenProductUnknown` test's already-documented pattern — assert with `anySatisfy`, not an exact list.
- Anonymous subscription-establishment failure surfaces as a `SubscriptionErrorException` (`org.springframework.graphql.client.SubscriptionErrorException`) classified `INTERNAL_ERROR` — **not** our `UNAUTHORIZED` — because subscription-establishment exceptions bypass `DemoExceptionResolver` entirely (verified live, twice, before and after Task 3's resolver changes). This is a genuine Spring GraphQL scope-limit documented in the module README (Task 7), not a bug.
- A mixed mutation (`addReview` + `deleteReview` in one operation, called as USER) → `addReview` succeeds and its data is present, `deleteReview` is denied with exactly one `FORBIDDEN` error, because `deleteReview`'s nullable `Boolean` return type (Task 4) doesn't null-propagate past itself.

- [ ] **Step 1: Add auth header helpers**

In `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/DemoIntegrationTest.java`, add these imports:

```java
import org.springframework.graphql.test.tester.WebSocketGraphQlTester;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
```

(`WebSocketGraphQlTester` is already imported — keep only one copy.)

Add these private helper methods at the end of the class, before the closing brace:

```java
	private HttpGraphQlTester asUser() {
		return graphQlTester.mutate().header("Authorization", basicAuthHeader("user", "userPassword")).build();
	}

	private HttpGraphQlTester asAdmin() {
		return graphQlTester.mutate().header("Authorization", basicAuthHeader("admin", "adminPassword")).build();
	}

	private static String basicAuthHeader(String username, String password) {
		String credentials = username + ":" + password;
		return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
	}
```

- [ ] **Step 2: Update `mutation_addReview_returnsCreatedReview` to authenticate**

Rename and update the existing test (it now requires auth since Task 4 added `@PreAuthorize("isAuthenticated()")`):

```java
	@Test
	void mutation_addReview_succeeds_whenAuthenticatedAsUser() {
		asUser().document("""
				mutation {
				  addReview(input: { productId: "p1", author: "Jordan", rating: 5, comment: "Great product" }) {
				    author
				    rating
				    comment
				  }
				}
				""").execute().path("addReview.author").entity(String.class).isEqualTo("Jordan");
	}
```

- [ ] **Step 3: Update `mutation_addReview_isRejected_whenProductUnknown` to authenticate**

```java
	@Test
	void mutation_addReview_isRejected_whenProductUnknown() {
		asUser().document("""
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

(This test's body doesn't change — it already used `asUser()`-shaped defenses via `anySatisfy`; the diff is that the call is now made through `asUser()` instead of the raw anonymous `graphQlTester`.)

- [ ] **Step 4: Add anonymous-rejection tests for `addReview` and `deleteReview`**

Add these new tests:

```java
	@Test
	void mutation_addReview_isRejected_whenAnonymous() {
		graphQlTester.document("""
				mutation {
				  addReview(input: { productId: "p1", author: "Jordan", rating: 5, comment: "x" }) { id }
				}
				""").execute().errors().satisfy(errors -> assertThat(errors)
				.anySatisfy(error -> assertThat(error.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED)));
	}

	@Test
	void mutation_deleteReview_isRejected_whenAnonymous() {
		graphQlTester.document("""
				mutation {
				  deleteReview(id: "does-not-matter")
				}
				""").execute().errors().satisfy(errors -> {
			assertThat(errors).hasSize(1);
			assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
		});
	}

	@Test
	void mutation_deleteReview_isRejected_whenAuthenticatedAsUser() {
		asUser().document("""
				mutation {
				  deleteReview(id: "does-not-matter")
				}
				""").execute().errors().satisfy(errors -> {
			assertThat(errors).hasSize(1);
			assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
		});
	}

	@Test
	void mutation_deleteReview_succeeds_whenAuthenticatedAsAdmin() {
		Review review = reviewService.addReview("p1", "Temp", 3, "to be deleted");

		asAdmin().document("""
				mutation {
				  deleteReview(id: "%s")
				}
				""".formatted(review.id())).execute().path("deleteReview").entity(Boolean.class).isEqualTo(true);
	}

	@Test
	void mutation_mixedFields_partiallyFails_whenUserLacksAdminRole() {
		List<ResponseError> errors = new ArrayList<>();

		asUser().document("""
				mutation {
				  addReview(input: { productId: "p1", author: "Casey", rating: 5, comment: "Nice" }) { id author }
				  deleteReview(id: "does-not-matter")
				}
				""").execute().errors().satisfy(errors::addAll).path("addReview.author").entity(String.class)
				.isEqualTo("Casey");

		assertThat(errors).hasSize(1);
		assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
	}
```

- [ ] **Step 5: Update the subscription test to authenticate, and add the anonymous-rejection subscription test**

Replace the existing `subscription_streamsReviewAdded_whenMutationPublishes` test with:

```java
	@Test
	void subscription_streamsReviewAdded_whenMutationPublishes() {
		WebSocketGraphQlTester authenticatedTester = webSocketGraphQlTester.mutate()
				.header("Authorization", basicAuthHeader("user", "userPassword")).build();
		try {
			Flux<Review> subscription = authenticatedTester.document("""
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

			// thenAwait: the WebSocket "subscribe" message needs a round trip to the server before DemoController's
			// reviewAdded() resolver is actually registered on the sink; without this gap the mutation below can fire
			// (and directBestEffort() will drop it) before the subscription is live server-side.
			StepVerifier.create(subscription).thenAwait(Duration.ofSeconds(2)).then(() -> asUser().document("""
					mutation {
					  addReview(input: { productId: "p1", author: "Riley", rating: 4, comment: "Solid" }) {
					    id
					  }
					}
					""").execute()).assertNext(review -> assertThat(review.author()).isEqualTo("Riley")).thenCancel()
					.verify(Duration.ofSeconds(10));
		} finally {
			authenticatedTester.stop().block();
		}
	}

	@Test
	void subscription_isRejected_whenAnonymous() {
		Flux<Review> subscription = webSocketGraphQlTester.document("""
				subscription {
				  reviewAdded(productId: "p1") {
				    id
				  }
				}
				""").executeSubscription().toFlux("reviewAdded", Review.class);

		// Subscription-establishment authorization failures don't route through DemoExceptionResolver the way
		// query/mutation errors do (verified live, both before and after Task 3's resolver changes) — the client
		// instead sees a generic SubscriptionErrorException classified INTERNAL_ERROR, not our UNAUTHORIZED. This
		// is a documented Spring GraphQL scope-limit, not a bug this demo works around.
		StepVerifier.create(subscription).expectErrorSatisfies(error -> {
			assertThat(error).isInstanceOf(SubscriptionErrorException.class);
			SubscriptionErrorException subscriptionError = (SubscriptionErrorException) error;
			assertThat(subscriptionError.getErrors()).anySatisfy(
					responseError -> assertThat(responseError.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR));
		}).verify(Duration.ofSeconds(10));
	}
```

Add this import for `SubscriptionErrorException`:

```java
import org.springframework.graphql.client.SubscriptionErrorException;
```

- [ ] **Step 6: Run the full integration test class**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn test -pl graphql/spring-demo -am -Dtest=DemoIntegrationTest` (from `communication-protocols/`)

Expected: PASS (all tests — the pre-existing ones unaffected by security plus every new one added in this task).

- [ ] **Step 7: Run the full module suite**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn test -pl graphql/spring-demo -am` (from `communication-protocols/`)

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/DemoIntegrationTest.java
git commit -m "test(communication-protocols): add authorization matrix to GraphQL DemoIntegrationTest"
```

---

### Task 6: Load tests — authenticate `addReview` (Gatling and JMeter)

**Files:**
- Modify: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/performance/DemoSimulation.java`
- Modify: `communication-protocols/graphql/spring-demo/src/test/jmeter/DemoSimulation.jmx`

**Interfaces:**
- Consumes: nothing new.
- Produces: nothing consumed elsewhere — this is a leaf change.

This module has both a Gatling test (`mvn gatling:test`) and a JMeter test (`mvn verify -Pjmeter-load-test`) driving the same three requests. Both hit `addReview`, which now requires auth (Task 4), so both need a Basic-auth header added.

- [ ] **Step 1: Add a Basic-auth header to the `addReview` request**

In `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/performance/DemoSimulation.java`, add this import:

```java
import java.nio.charset.StandardCharsets;
import java.util.Base64;
```

Add this constant directly after `ADD_REVIEW_MUTATION_BODY`:

```java
	private static final String USER_BASIC_AUTH_HEADER = "Basic "
			+ Base64.getEncoder().encodeToString("user:userPassword".getBytes(StandardCharsets.UTF_8));
```

Change the `addReview` step in `demoScenario` from:

```java
.pause(Duration.ofMillis(500)).exec(http("Mutation - Add Review").post("/graphql")
        .body(StringBody(ADD_REVIEW_MUTATION_BODY)).check(status().is(200)));
```

to:

```java
.pause(Duration.ofMillis(500)).exec(http("Mutation - Add Review").post("/graphql")
        .header("Authorization", USER_BASIC_AUTH_HEADER).body(StringBody(ADD_REVIEW_MUTATION_BODY))
        .check(status().is(200)));
```

- [ ] **Step 2: Run the load test to verify it still passes**

Run (from `communication-protocols/`, app must already be running via `mvn -pl graphql/spring-demo spring-boot:run` — with the security changes from Tasks 1–5 built in, use `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home` for both commands):

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn gatling:test -pl graphql/spring-demo
```

Expected: all three requests (`Query - Products with Reviews`, `Query - Product by Id`, `Mutation - Add Review`) report 0 KO (0 failed) in the console summary — `Add Review` would report 100% KO before this step's header addition, since it now requires auth.

- [ ] **Step 3: Add the same Basic-auth header to the JMeter test**

In `communication-protocols/graphql/spring-demo/src/test/jmeter/DemoSimulation.jmx`, the shared `HTTP Header Manager` (applies to all three requests in the thread group) currently sets only `Content-Type`:

```xml
        <HeaderManager guiclass="HeaderPanel" testclass="HeaderManager" testname="HTTP Header Manager" enabled="true">
          <collectionProp name="HeaderManager.headers">
            <elementProp name="" elementType="Header">
              <stringProp name="Header.name">Content-Type</stringProp>
              <stringProp name="Header.value">application/json</stringProp>
            </elementProp>
          </collectionProp>
        </HeaderManager>
```

Add a second `elementProp` for the `Authorization` header (base64 of `user:userPassword` is `dXNlcjp1c2VyUGFzc3dvcmQ=` — verified with `echo -n "user:userPassword" | base64`). The header is added at the shared thread-group level rather than scoped to just the `Mutation - Add Review` sampler, matching how `Content-Type` is already applied uniformly — harmless for the two public queries, which don't care whether an `Authorization` header is present:

```xml
        <HeaderManager guiclass="HeaderPanel" testclass="HeaderManager" testname="HTTP Header Manager" enabled="true">
          <collectionProp name="HeaderManager.headers">
            <elementProp name="" elementType="Header">
              <stringProp name="Header.name">Content-Type</stringProp>
              <stringProp name="Header.value">application/json</stringProp>
            </elementProp>
            <elementProp name="" elementType="Header">
              <stringProp name="Header.name">Authorization</stringProp>
              <stringProp name="Header.value">Basic dXNlcjp1c2VyUGFzc3dvcmQ=</stringProp>
            </elementProp>
          </collectionProp>
        </HeaderManager>
```

- [ ] **Step 4: Run the JMeter load test to verify it still passes**

Run (from `communication-protocols/`, app must already be running with the security changes from Tasks 1–5 built in):

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn verify -Pjmeter-load-test -pl graphql/spring-demo
```

Expected: 0 errors reported in the console summary and in `target/jmeter/results/*.csv`; `Mutation - Add Review` no longer returns 401s.

- [ ] **Step 5: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/performance/DemoSimulation.java \
        communication-protocols/graphql/spring-demo/src/test/jmeter/DemoSimulation.jmx
git commit -m "test(communication-protocols): authenticate addReview in the GraphQL load tests"
```

---

### Task 7: README — Security section

**Files:**
- Modify: `communication-protocols/graphql/spring-demo/README.md`

**Interfaces:** None — documentation only.

- [ ] **Step 1: Add the Security section**

In `communication-protocols/graphql/spring-demo/README.md`, insert a new section directly before `## Build & test`:

```markdown
## Security

Three operations are gated by HTTP Basic auth and Spring Security method security (`@PreAuthorize` on `DemoController`), demonstrating that GraphQL authorization is field-level, not URL-level — there's only one endpoint (`/graphql`) for every operation:

| Operation | Rule |
|---|---|
| `products`, `product(id)` | Public — no annotation |
| `addReview`, `reviewAdded` (subscription) | `isAuthenticated()` — any of the two demo users |
| `deleteReview(id)` | `hasRole('ADMIN')` — the one action where the two demo users behave differently |

Demo users (same credentials as `backend/rest-api`, in-memory, not for production use): `user`/`userPassword` (ROLE_USER), `admin`/`adminPassword` (ROLE_ADMIN).

**Anonymous — public query still works:**

```bash
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ products { id } }"}'
```

**Anonymous — protected mutation is rejected:**

```bash
curl -s http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"mutation { addReview(input: { productId: \"p1\", author: \"a\", rating: 5, comment: \"c\" }) { id } }"}'
# {"errors":[{"message":"Access Denied", "extensions":{"classification":"UNAUTHORIZED"}}, ...],"data":null}
```

**USER — allowed to add a review, forbidden from deleting one:**

```bash
curl -s -u user:userPassword http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"mutation { addReview(input: { productId: \"p1\", author: \"Jordan\", rating: 5, comment: \"Great product\" }) { id author } }"}'

curl -s -u user:userPassword http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"mutation { deleteReview(id: \"some-review-id\") }"}'
# {"errors":[{"message":"Access Denied", "extensions":{"classification":"FORBIDDEN"}}],"data":{"deleteReview":null}}
```

**ADMIN — allowed to delete a review:**

```bash
curl -s -u admin:adminPassword http://localhost:8092/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"mutation { deleteReview(id: \"some-review-id\") }"}'
# {"data":{"deleteReview":true}}
```

**Scope limits:**

- Demo credentials only — HTTP Basic, plaintext (`{noop}`-prefixed) in-memory users, matching this repo's "local demo" scope everywhere else. Not a production security guide: no JWT/OAuth2/OIDC, no real password encoding, no per-user data ownership (roles gate *actions*, not *which data a user can see*).
- CSRF is disabled in `SecurityConfig` — matches `backend/rest-api`, and is irrelevant here since this is a stateless Basic-auth API with no cookie-based session.
- Subscription-establishment authorization failures don't go through the same error classification as query/mutation fields — an unauthenticated `reviewAdded` subscription attempt fails with a generic transport-level error (`SubscriptionErrorException`, classified `INTERNAL_ERROR`) rather than `UNAUTHORIZED`. This is a Spring GraphQL limitation for this failure mode, not something this demo works around.
```

- [ ] **Step 2: Verify the doc renders sensibly**

No automated check for markdown; read the file back and confirm the table and code fences are well-formed.

- [ ] **Step 3: Commit**

```bash
git add communication-protocols/graphql/spring-demo/README.md
git commit -m "docs(communication-protocols): document GraphQL security/roles in the module README"
```

---

## Final verification

- [ ] Run `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn clean package -pl graphql/spring-demo -am` from `communication-protocols/` — expect BUILD SUCCESS.
- [ ] Start the app (`mvn -pl graphql/spring-demo spring-boot:run`) and manually verify the four `curl` examples in the new README section against a live instance.
