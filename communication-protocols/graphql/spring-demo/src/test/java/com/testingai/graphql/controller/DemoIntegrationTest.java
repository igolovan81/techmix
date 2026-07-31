package com.testingai.graphql.controller;

import com.testingai.graphql.domain.Review;
import com.testingai.graphql.domain.ReviewService;
import com.testingai.graphql.domain.Role;
import com.testingai.graphql.entity.CategoryEntity;
import com.testingai.graphql.entity.ProductEntity;
import com.testingai.graphql.entity.UserEntity;
import com.testingai.graphql.repository.CategoryRepository;
import com.testingai.graphql.repository.ProductRepository;
import com.testingai.graphql.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.client.SubscriptionErrorException;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.graphql.test.tester.WebSocketGraphQlTester;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.socket.client.TomcatWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoIntegrationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private ReviewService reviewService;
	@Autowired
	private ProductRepository productRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private CategoryRepository categoryRepository;

	private HttpGraphQlTester graphQlTester;
	private WebSocketGraphQlTester webSocketGraphQlTester;

	private Long productId1;

	@BeforeEach
	void setUpTesters() {
		WebTestClient webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port + "/graphql")
				.build();
		graphQlTester = HttpGraphQlTester.create(webTestClient);
		webSocketGraphQlTester = WebSocketGraphQlTester
				.builder("ws://localhost:" + port + "/graphql", new TomcatWebSocketClient()).build();

		// SpringBootTest doesn't roll back between tests, and the "user"/"admin" demo accounts (SecurityConfig)
		// must have matching User rows for addReview's principal-to-author resolution to work — idempotent so a
		// later test method's call is a no-op once an earlier one has already created them.
		ensureUser("user", Role.CUSTOMER);
		ensureUser("admin", Role.ADMIN);

		productId1 = saveProduct("Mini Widget", 636, 50);
	}

	@AfterEach
	void stopWebSocketTester() {
		webSocketGraphQlTester.stop().block();
	}

	private void ensureUser(String username, Role role) {
		if (userRepository.findByUsername(username).isEmpty()) {
			UserEntity user = new UserEntity();
			user.setUsername(username);
			user.setEmail(username + "@example.com");
			user.setDisplayName(username);
			user.setRole(role);
			userRepository.save(user);
		}
	}

	private Long saveProduct(String name, long priceCents, int stockQty) {
		ProductEntity entity = new ProductEntity();
		entity.setName(name);
		entity.setPriceCents(priceCents);
		entity.setStockQty(stockQty);
		return productRepository.save(entity).getId();
	}

	private CategoryEntity saveCategory(String name, CategoryEntity parent) {
		CategoryEntity entity = new CategoryEntity();
		entity.setName(name);
		entity.setParent(parent);
		return categoryRepository.save(entity);
	}

	// A per-test-method unique tag so filtered count/pagination assertions aren't polluted by fixture rows other
	// test methods leave behind (this class's @SpringBootTest doesn't roll back between tests).
	private String uniqueTag() {
		return "tag" + System.nanoTime();
	}

	@Test
	void query_returnsFirstPageOfProducts_byDefault() {
		String tag = uniqueTag();
		for (int i = 0; i < 15; i++) {
			saveProduct(tag + "-Item" + i, 1000 + i, 10);
		}

		graphQlTester.document("""
				query {
				  products(filter: { nameContains: "%s" }) {
				    edges { node { id name } cursor }
				    pageInfo { hasNextPage endCursor }
				    totalCount
				  }
				}
				""".formatted(tag)).execute().path("products.edges").entityList(Object.class).hasSize(10)
				.path("products.totalCount").entity(Integer.class).isEqualTo(15).path("products.pageInfo.hasNextPage")
				.entity(Boolean.class).isEqualTo(true);
	}

	@Test
	void query_pagesThroughAllProducts_usingEndCursor() {
		String tag = uniqueTag();
		for (int i = 0; i < 20; i++) {
			saveProduct(tag + "-Item" + i, 1000 + i, 10);
		}

		String firstPageQuery = """
				query {
				  products(filter: { nameContains: "%s" }, first: 15) {
				    edges { node { id } cursor }
				    pageInfo { hasNextPage endCursor }
				  }
				}
				""".formatted(tag);

		String firstEndCursor = graphQlTester.document(firstPageQuery).execute().path("products.pageInfo.endCursor")
				.entity(String.class).get();

		graphQlTester.document("""
				query {
				  products(filter: { nameContains: "%s" }, first: 15, after: "%s") {
				    edges { node { id } }
				    pageInfo { hasNextPage }
				  }
				}
				""".formatted(tag, firstEndCursor)).execute().path("products.edges").entityList(Object.class).hasSize(5)
				.path("products.pageInfo.hasNextPage").entity(Boolean.class).isEqualTo(false);
	}

	@Test
	void query_filtersProductsByNameAndPriceRange() {
		String tag = uniqueTag();
		saveProduct(tag + "-mini-widget", 1000, 10);
		saveProduct(tag + "-standard-widget", 5000, 10);

		graphQlTester.document("""
				query {
				  products(filter: { nameContains: "%s-mini" }, first: 50) {
				    edges { node { name } }
				    totalCount
				  }
				}
				""".formatted(tag)).execute().path("products.totalCount").entity(Integer.class).isEqualTo(1)
				.path("products.edges").entityList(java.util.Map.class)
				.satisfies(edges -> assertThat(edges).hasSize(1)
						.allSatisfy(edge -> assertThat(
								((java.util.Map<?, ?>) edge.get("node")).get("name").toString().toLowerCase())
								.contains("mini")));
	}

	@Test
	void query_returnsOneProduct_byId() {
		// product(id) has a real 5% simulated failure (FailureSimulator), so a single call can spuriously fail;
		// retry past that (same statistical approach used for the other FailureSimulator-affected tests below).
		for (int attempt = 0; attempt < 20; attempt++) {
			List<ResponseError> errors = new ArrayList<>();
			GraphQlTester.Traversable afterErrors = graphQlTester.document("""
					query {
					  product(id: "%s") { id name }
					}
					""".formatted(productId1)).execute().errors().satisfy(errors::addAll);

			if (errors.isEmpty()) {
				afterErrors.path("product.name").entity(String.class).isEqualTo("Mini Widget");
				return;
			}
		}

		fail("product query kept simulating failure across 20 attempts (5% failure rate)");
	}

	@Test
	void query_returnsProductsWithNestedReviews_batchedInOneCall() {
		String tag = uniqueTag();
		for (int i = 0; i < 5; i++) {
			saveProduct(tag + "-Item" + i, 1000 + i, 10);
		}
		int batchCallsBefore = reviewService.getBatchCallCount();

		graphQlTester.document("""
				query {
				  products(filter: { nameContains: "%s" }, first: 50) {
				    edges {
				      node {
				        id
				        name
				        reviews { edges { node { id rating } } }
				      }
				    }
				  }
				}
				""".formatted(tag)).execute().path("products.edges").entityList(Object.class).hasSize(5);

		assertThat(reviewService.getBatchCallCount()).isEqualTo(batchCallsBefore + 1);
	}

	@Test
	void query_categoryTree_resolvesParentAndChildren() {
		CategoryEntity root = saveCategory("Electronics-" + uniqueTag(), null);
		CategoryEntity child = saveCategory("Audio-" + uniqueTag(), root);

		graphQlTester.document("""
				query {
				  category(id: "%s") {
				    name
				    parent { id }
				    children(first: 10) { edges { node { id name } } totalCount }
				  }
				}
				""".formatted(root.getId())).execute().path("category.parent").valueIsNull()
				.path("category.children.totalCount").entity(Integer.class).isEqualTo(1)
				.path("category.children.edges[0].node.name").entity(String.class).isEqualTo(child.getName());

		graphQlTester.document("""
				query {
				  category(id: "%s") { parent { name } }
				}
				""".formatted(child.getId())).execute().path("category.parent.name").entity(String.class)
				.isEqualTo(root.getName());
	}

	@Test
	void query_categoryProducts_returnsOnlyProductsInThatCategory() {
		CategoryEntity category = saveCategory("Category-" + uniqueTag(), null);
		String tag = uniqueTag();

		ProductEntity inCategory = new ProductEntity();
		inCategory.setName(tag + "-in-category");
		inCategory.setPriceCents(1000);
		inCategory.setStockQty(10);
		inCategory.getCategories().add(category);
		productRepository.save(inCategory);

		saveProduct(tag + "-outside-category", 1000, 10);

		graphQlTester.document("""
				query {
				  category(id: "%s") {
				    products(first: 50) { totalCount edges { node { name } } }
				  }
				}
				""".formatted(category.getId())).execute().path("category.products.totalCount").entity(Integer.class)
				.isEqualTo(1).path("category.products.edges[0].node.name").entity(String.class)
				.isEqualTo(inCategory.getName());
	}

	@Test
	void query_productCategories_resolvesViaBatchMapping() {
		CategoryEntity categoryA = saveCategory("CategoryA-" + uniqueTag(), null);
		CategoryEntity categoryB = saveCategory("CategoryB-" + uniqueTag(), null);
		String tag = uniqueTag();

		ProductEntity product = new ProductEntity();
		product.setName(tag + "-multi-category-product");
		product.setPriceCents(1000);
		product.setStockQty(10);
		product.getCategories().add(categoryA);
		product.getCategories().add(categoryB);
		productRepository.save(product);

		graphQlTester.document("""
				query {
				  products(filter: { nameContains: "%s" }, first: 10) {
				    edges { node { categories { name } } }
				  }
				}
				""".formatted(tag)).execute().path("products.edges[0].node.categories").entityList(java.util.Map.class)
				.satisfies(categories -> assertThat(categories).extracting(c -> c.get("name"))
						.containsExactlyInAnyOrder(categoryA.getName(), categoryB.getName()));
	}

	@Test
	void query_categories_respectsFirstArgument() {
		graphQlTester.document("""
				query {
				  categories(first: 1) {
				    edges { node { id } }
				    pageInfo { hasNextPage }
				  }
				}
				""").execute().path("categories.edges").entityList(Object.class).hasSize(1)
				.path("categories.pageInfo.hasNextPage").entity(Boolean.class).isEqualTo(true);
	}

	@Test
	void query_filtersReviewsByMinRating() {
		asUser().document("""
				mutation {
				  addReview(input: { productId: "%s", rating: 2, comment: "meh" }) { id }
				}
				""".formatted(productId1)).execute();
		asUser().document("""
				mutation {
				  addReview(input: { productId: "%s", rating: 5, comment: "great" }) { id }
				}
				""".formatted(productId1)).execute();

		String query = """
				query {
				  product(id: "%s") {
				    reviews(filter: { minRating: 4 }, first: 10) {
				      edges { node { rating } }
				    }
				  }
				}
				""".formatted(productId1);

		// product(id) has a real 5% simulated failure (FailureSimulator), so a single call can spuriously fail;
		// retry past that (same statistical approach used elsewhere in this file).
		for (int attempt = 0; attempt < 20; attempt++) {
			List<ResponseError> errors = new ArrayList<>();
			GraphQlTester.Traversable afterErrors = graphQlTester.document(query).execute().errors()
					.satisfy(errors::addAll);

			if (errors.isEmpty()) {
				afterErrors.path("product.reviews.edges").entityList(java.util.Map.class)
						.satisfies(edges -> assertThat(edges).isNotEmpty().allSatisfy(
								edge -> assertThat((Integer) ((java.util.Map<?, ?>) edge.get("node")).get("rating"))
										.isGreaterThanOrEqualTo(4)));
				return;
			}
		}

		fail("product query kept simulating failure across 20 attempts (5% failure rate)");
	}

	@Test
	void mutation_addReview_succeeds_whenAuthenticatedAsUser() {
		asUser().document("""
				mutation {
				  addReview(input: { productId: "%s", rating: 5, comment: "Great product" }) {
				    author { username }
				    rating
				    comment
				  }
				}
				""".formatted(productId1)).execute().path("addReview.author.username").entity(String.class)
				.isEqualTo("user");
	}

	@Test
	void mutation_addReview_isRejected_whenProductUnknown() {
		asUser().document("""
				mutation {
				  addReview(input: { productId: "999999999", rating: 5, comment: "x" }) {
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

	@Test
	void mutation_addReview_isRejected_whenAnonymous() {
		graphQlTester.document("""
				mutation {
				  addReview(input: { productId: "%s", rating: 5, comment: "x" }) { id }
				}
				""".formatted(productId1)).execute().errors().satisfy(errors -> assertThat(errors)
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
		Long userId = userRepository.findByUsername("user").orElseThrow().getId();
		Review review = reviewService.addReview(productId1.toString(), userId, 3, "to be deleted");

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
				  addReview(input: { productId: "%s", rating: 5, comment: "Nice" }) { id author { username } }
				  deleteReview(id: "does-not-matter")
				}
				""".formatted(productId1)).execute().errors().satisfy(errors::addAll).path("addReview.author.username")
				.entity(String.class).isEqualTo("user");

		assertThat(errors).hasSize(1);
		assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
	}

	@Test
	void subscription_streamsReviewAdded_whenMutationPublishes() {
		WebSocketGraphQlTester authenticatedTester = webSocketGraphQlTester.mutate()
				.header("Authorization", basicAuthHeader("user", "userPassword")).build();
		try {
			Flux<java.util.Map> subscription = authenticatedTester.document("""
					subscription {
					  reviewAdded(productId: "%s") {
					    id
					    productId
					    author { username }
					    rating
					    comment
					  }
					}
					""".formatted(productId1)).executeSubscription().toFlux("reviewAdded", java.util.Map.class);

			// thenAwait: the WebSocket "subscribe" message needs a round trip to the server before DemoController's
			// reviewAdded() resolver is actually registered on the sink; without this gap the mutation below can fire
			// (and directBestEffort() will drop it) before the subscription is live server-side.
			StepVerifier.create(subscription).thenAwait(Duration.ofSeconds(2)).then(() -> asUser().document("""
					mutation {
					  addReview(input: { productId: "%s", rating: 4, comment: "Solid" }) {
					    id
					  }
					}
					""".formatted(productId1)).execute())
					.assertNext(review -> assertThat(((java.util.Map<?, ?>) review.get("author")).get("username"))
							.isEqualTo("user"))
					.thenCancel().verify(Duration.ofSeconds(10));
		} finally {
			authenticatedTester.stop().block();
		}
	}

	@Test
	void subscription_isRejected_whenAnonymous() {
		Flux<java.util.Map> subscription = webSocketGraphQlTester.document("""
				subscription {
				  reviewAdded(productId: "does-not-matter") {
				    id
				  }
				}
				""").executeSubscription().toFlux("reviewAdded", java.util.Map.class);

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

	@Test
	void query_partiallyFails_whenProductLookupSimulatesFailure() {
		// FailureSimulator's 5% failure is real (not mocked): the GraphQL execution runs on a Tomcat worker
		// thread, not the test thread, so Mockito's thread-confined mockStatic can't reach it here. Instead,
		// repeat until one of the ~5%-chance failures actually happens (same statistical approach as
		// FailureSimulatorTest) and assert on that response's partial-failure shape.
		String tag = uniqueTag();
		for (int i = 0; i < 10; i++) {
			saveProduct(tag + "-Item" + i, 1000 + i, 10);
		}

		String query = """
				query {
				  products { edges { node { id } } }
				  product(id: "%s") { id name }
				}
				""".formatted(productId1);

		for (int attempt = 0; attempt < 200; attempt++) {
			List<ResponseError> errors = new ArrayList<>();
			GraphQlTester.Traversable afterErrors = graphQlTester.document(query).execute().errors()
					.satisfy(errors::addAll);

			if (!errors.isEmpty()) {
				assertThat(errors).hasSize(1);
				assertThat(errors.get(0).getMessage()).isEqualTo("Simulated 5% failure in product query");
				assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
				afterErrors.path("product").valueIsNull();
				afterErrors.path("products.edges").entityList(Object.class).hasSize(10);
				return;
			}
		}

		fail("Expected at least one simulated failure across 200 attempts (5% failure rate)");
	}

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
}
