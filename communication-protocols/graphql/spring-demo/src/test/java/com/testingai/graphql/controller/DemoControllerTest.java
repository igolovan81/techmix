package com.testingai.graphql.controller;

import com.testingai.graphql.domain.AddReviewInput;
import com.testingai.graphql.domain.Product;
import com.testingai.graphql.domain.ProductCatalogService;
import com.testingai.graphql.domain.Review;
import com.testingai.graphql.domain.ReviewService;
import com.testingai.graphql.util.FailureSimulator;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

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

	@Test
	void reviews_batchesAllProducts_inOneCall() {
		List<Product> products = productCatalogService.listProducts().subList(0, 3);

		Map<Product, List<Review>> reviewsByProduct = controller.reviews(products);

		assertThat(reviewsByProduct).hasSize(3);
		assertThat(reviewService.getBatchCallCount()).isEqualTo(1);
	}

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

	@Test
	void reviewAdded_filtersByProductId() {
		StepVerifier.create(controller.reviewAdded("p1").take(1))
				.then(() -> controller.addReview(new AddReviewInput("p2", "Jordan", 5, "not p1")))
				.then(() -> controller.addReview(new AddReviewInput("p1", "Sam", 4, "for p1")))
				.assertNext(review -> assertThat(review.productId()).isEqualTo("p1")).verifyComplete();
	}
}
