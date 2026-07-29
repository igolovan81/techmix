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
	// directBestEffort(): delivered only to subscribers already connected at emission time, nothing queued for
	// subscribers that haven't connected yet — the right semantics for "subscribe to reviews from now on" (an
	// onBackpressureBuffer() sink would instead buffer emissions indefinitely until the first-ever subscriber
	// connects, then replay them, which is both a memory leak risk and observably wrong for a live subscription).
	private final Sinks.Many<Review> reviewAddedSink = Sinks.many().multicast().directBestEffort();
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
	 * Batch-fetches reviews for every product id in one call — the DataLoader pattern that avoids the N+1 problem when
	 * resolving {@code Product.reviews} for a list of products in a single GraphQL query.
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
