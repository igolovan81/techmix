package com.testingai.graphql.domain;

import com.testingai.graphql.entity.ProductEntity;
import com.testingai.graphql.entity.ReviewEntity;
import com.testingai.graphql.entity.UserEntity;
import com.testingai.graphql.repository.ProductRepository;
import com.testingai.graphql.repository.ReviewRepository;
import com.testingai.graphql.repository.UserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Slf4j
@Service
public class ReviewService {

	private final ReviewRepository reviewRepository;
	private final ProductRepository productRepository;
	private final UserRepository userRepository;
	// directBestEffort(): delivered only to subscribers already connected at emission time, nothing queued for
	// subscribers that haven't connected yet — the right semantics for "subscribe to reviews from now on" (an
	// onBackpressureBuffer() sink would instead buffer emissions indefinitely until the first-ever subscriber
	// connects, then replay them, which is both a memory leak risk and observably wrong for a live subscription).
	private final Sinks.Many<Review> reviewAddedSink = Sinks.many().multicast().directBestEffort();
	private final AtomicInteger batchCallCount = new AtomicInteger();

	public ReviewService(ReviewRepository reviewRepository, ProductRepository productRepository,
			UserRepository userRepository) {
		this.reviewRepository = reviewRepository;
		this.productRepository = productRepository;
		this.userRepository = userRepository;
	}

	public Map<String, List<Review>> findByProductIds(List<String> productIds) {
		return findByProductIds(productIds, null);
	}

	@Transactional(readOnly = true)
	public Map<String, List<Review>> findByProductIds(List<String> productIds, ReviewFilter filter) {
		batchCallCount.incrementAndGet();
		log.info("batch fetching reviews for {} products in one call", productIds.size());
		List<Long> ids = productIds.stream().map(Long::parseLong).toList();
		Map<Long, List<Review>> byProductId = reviewRepository.findByProductIdIn(ids).stream()
				.map(ReviewService::toReview).collect(Collectors.groupingBy(
						review -> Long.parseLong(review.productId()), LinkedHashMap::new, Collectors.toList()));

		Map<String, List<Review>> result = new LinkedHashMap<>();
		for (String productId : productIds) {
			List<Review> reviews = byProductId.getOrDefault(Long.parseLong(productId), List.of());
			result.put(productId, filterReviews(reviews, filter));
		}
		return result;
	}

	public List<Review> filterReviews(List<Review> reviews, ReviewFilter filter) {
		if (filter == null || filter.minRating() == null) {
			return reviews;
		}
		return reviews.stream().filter(review -> review.rating() >= filter.minRating()).toList();
	}

	@Transactional
	public Review addReview(String productId, Long authorId, int rating, String comment) {
		ProductEntity product = productRepository.findById(Long.parseLong(productId))
				.orElseThrow(() -> new IllegalArgumentException("Unknown product: " + productId));
		UserEntity author = userRepository.findById(authorId)
				.orElseThrow(() -> new NoSuchElementException("Unknown user: " + authorId));

		ReviewEntity entity = new ReviewEntity();
		entity.setProduct(product);
		entity.setAuthor(author);
		entity.setRating(rating);
		entity.setComment(comment);
		Review review = toReview(reviewRepository.save(entity));

		reviewAddedSink.tryEmitNext(review);
		return review;
	}

	public Flux<Review> reviewAdded() {
		return reviewAddedSink.asFlux();
	}

	@Transactional
	public boolean deleteReview(String reviewId) {
		UUID id;
		try {
			id = UUID.fromString(reviewId);
		} catch (IllegalArgumentException e) {
			return false;
		}
		if (!reviewRepository.existsById(id)) {
			return false;
		}
		reviewRepository.deleteById(id);
		return true;
	}

	public int getBatchCallCount() {
		return batchCallCount.get();
	}

	static Review toReview(ReviewEntity entity) {
		return new Review(entity.getId().toString(), entity.getProduct().getId().toString(), entity.getAuthor().getId(),
				entity.getRating(), entity.getComment());
	}
}
