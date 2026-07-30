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
}
