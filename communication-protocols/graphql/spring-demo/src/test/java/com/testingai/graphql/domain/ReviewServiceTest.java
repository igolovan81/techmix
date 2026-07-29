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
		StepVerifier.create(service.reviewAdded()).then(() -> service.addReview("p1", "Jordan", 5, "Great product"))
				.assertNext(review -> {
					assertThat(review.author()).isEqualTo("Jordan");
					assertThat(review.productId()).isEqualTo("p1");
				}).thenCancel().verify();

		assertThat(service.findByProductIds(List.of("p1")).get("p1"))
				.anyMatch(review -> review.author().equals("Jordan"));
	}
}
