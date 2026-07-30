package com.testingai.graphql.controller;

import com.testingai.graphql.domain.Review;
import com.testingai.graphql.domain.ReviewService;
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

	private HttpGraphQlTester graphQlTester;
	private WebSocketGraphQlTester webSocketGraphQlTester;

	@BeforeEach
	void setUpTesters() {
		WebTestClient webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port + "/graphql")
				.build();
		graphQlTester = HttpGraphQlTester.create(webTestClient);
		webSocketGraphQlTester = WebSocketGraphQlTester
				.builder("ws://localhost:" + port + "/graphql", new TomcatWebSocketClient()).build();
	}

	@AfterEach
	void stopWebSocketTester() {
		webSocketGraphQlTester.stop().block();
	}

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
				.satisfies(edges -> assertThat(edges).isNotEmpty()
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
					  product(id: "p1") { id name }
					}
					""").execute().errors().satisfy(errors::addAll);

			if (errors.isEmpty()) {
				afterErrors.path("product.name").entity(String.class).isEqualTo("Mini Widget");
				return;
			}
		}

		fail("product query kept simulating failure across 20 attempts (5% failure rate)");
	}

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

		String query = """
				query {
				  product(id: "p1") {
				    reviews(filter: { minRating: 4 }, first: 10) {
				      edges { node { rating } }
				    }
				  }
				}
				""";

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

	@Test
	void query_partiallyFails_whenProductLookupSimulatesFailure() {
		// FailureSimulator's 5% failure is real (not mocked): the GraphQL execution runs on a Tomcat worker
		// thread, not the test thread, so Mockito's thread-confined mockStatic can't reach it here. Instead,
		// repeat until one of the ~5%-chance failures actually happens (same statistical approach as
		// FailureSimulatorTest) and assert on that response's partial-failure shape.
		String query = """
				query {
				  products { edges { node { id } } }
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
