package com.testingai.graphql.domain;

import com.testingai.graphql.entity.ProductEntity;
import com.testingai.graphql.entity.UserEntity;
import com.testingai.graphql.repository.ProductRepository;
import com.testingai.graphql.repository.ReviewRepository;
import com.testingai.graphql.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReviewServiceTest {

	@Autowired
	private ProductRepository productRepository;
	@Autowired
	private ReviewRepository reviewRepository;
	@Autowired
	private UserRepository userRepository;

	private ReviewService service;
	private ProductEntity product;
	private UserEntity author;

	@BeforeEach
	void setUp() {
		service = new ReviewService(reviewRepository, productRepository, userRepository);

		product = new ProductEntity();
		product.setName("Widget");
		product.setPriceCents(999);
		product.setStockQty(10);
		product = productRepository.save(product);

		author = new UserEntity();
		author.setUsername("jordan");
		author.setEmail("jordan@example.com");
		author.setDisplayName("Jordan");
		author.setRole(Role.CUSTOMER);
		author = userRepository.save(author);
	}

	@Test
	void findByProductIds_batchesInOneCall() {
		Map<String, List<Review>> result = service.findByProductIds(List.of(product.getId().toString()));

		assertThat(result).containsOnlyKeys(product.getId().toString());
		assertThat(service.getBatchCallCount()).isEqualTo(1);
	}

	@Test
	void findByProductIds_returnsEmptyList_forProductWithNoReviews() {
		Map<String, List<Review>> result = service.findByProductIds(List.of(product.getId().toString()));

		assertThat(result.get(product.getId().toString())).isEmpty();
	}

	@Test
	void addReview_storesReview_andEmitsToSink() {
		String productId = product.getId().toString();

		StepVerifier.create(service.reviewAdded())
				.then(() -> service.addReview(productId, author.getId(), 5, "Great product")).assertNext(review -> {
					assertThat(review.authorId()).isEqualTo(author.getId());
					assertThat(review.productId()).isEqualTo(productId);
				}).thenCancel().verify();

		assertThat(service.findByProductIds(List.of(productId)).get(productId))
				.anyMatch(review -> review.authorId().equals(author.getId()));
	}

	@Test
	void addReview_throws_whenProductUnknown() {
		assertThatThrownBy(() -> service.addReview("999999", author.getId(), 5, "x"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void deleteReview_removesMatchingReview_andReturnsTrue() {
		Review review = service.addReview(product.getId().toString(), author.getId(), 5, "Great product");

		boolean deleted = service.deleteReview(review.id());

		assertThat(deleted).isTrue();
		assertThat(service.findByProductIds(List.of(product.getId().toString())).get(product.getId().toString()))
				.doesNotContain(review);
	}

	@Test
	void deleteReview_returnsFalse_whenReviewIdIsNotAValidUuid() {
		assertThat(service.deleteReview("not-a-uuid")).isFalse();
	}

	@Test
	void deleteReview_returnsFalse_whenReviewUnknown() {
		assertThat(service.deleteReview(java.util.UUID.randomUUID().toString())).isFalse();
	}

	@Test
	void findByProductIds_filtersByMinRating() {
		String productId = product.getId().toString();
		service.addReview(productId, author.getId(), 2, "meh");
		service.addReview(productId, author.getId(), 5, "great");

		List<Review> reviews = service.findByProductIds(List.of(productId), new ReviewFilter(4)).get(productId);

		assertThat(reviews).extracting(Review::rating).containsOnly(5);
	}

	@Test
	void findByProductIds_withFilter_stillBatchesInOneCall() {
		service.findByProductIds(List.of(product.getId().toString()), new ReviewFilter(3));

		assertThat(service.getBatchCallCount()).isEqualTo(1);
	}
}
