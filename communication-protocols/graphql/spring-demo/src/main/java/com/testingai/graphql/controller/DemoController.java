package com.testingai.graphql.controller;

import com.testingai.graphql.domain.AddReviewInput;
import com.testingai.graphql.domain.Product;
import com.testingai.graphql.domain.ProductCatalogService;
import com.testingai.graphql.domain.ProductFilter;
import com.testingai.graphql.domain.Review;
import com.testingai.graphql.domain.ReviewFilter;
import com.testingai.graphql.domain.ReviewService;
import com.testingai.graphql.pagination.Connection;
import com.testingai.graphql.pagination.CursorPagination;
import com.testingai.graphql.util.FailureSimulator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Hosts every GraphQL operation for this demo — queries, the batch-mapped {@code reviews} field, the mutation, and the
 * subscription all live here, mirroring how {@code grpc/client-demo}'s {@code DemoController} centralizes every RPC
 * pattern in one class.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class DemoController {

	private final ProductCatalogService productCatalogService;
	private final ReviewService reviewService;

	/**
	 * Query — returns a filtered, paginated page of the in-memory catalog. See {@link CursorPagination} for the
	 * pagination contract (forward-only, {@code first} defaults to 10 and is clamped to 50).
	 */
	@QueryMapping
	public Connection<Product> products(@Argument ProductFilter filter, @Argument Integer first,
			@Argument String after) {
		List<Product> filtered = productCatalogService.listProducts(filter);
		Connection<Product> page = CursorPagination.paginate(filtered, first, after);
		log.info("[products] returning {} of {} filtered products", page.edges().size(), filtered.size());
		return page;
	}

	/**
	 * Query — looks up one product by id. Has a 5% simulated failure via {@link FailureSimulator}, demonstrating
	 * GraphQL's partial-failure behavior: this field's error is reported in the response's {@code errors[]} array
	 * without failing sibling fields in the same request.
	 */
	@QueryMapping
	public Product product(@Argument String id) {
		log.info("[product] looking up productId={}", id);
		FailureSimulator.maybeThrow("product query");
		return productCatalogService.findProduct(id).orElse(null);
	}

	/**
	 * Batch mapping for {@code Product.reviews} — the DataLoader pattern. However many products are being resolved in a
	 * single query, this method runs exactly once, fetching every product's reviews in one call to
	 * {@link ReviewService#findByProductIds(List, ReviewFilter)} instead of once per product (the N+1 problem). Every
	 * product being resolved shares the same {@code filter}/{@code first}/{@code after} arguments — they come from one
	 * field selection in the query, not per-product.
	 */
	@BatchMapping
	public Map<Product, Connection<Review>> reviews(List<Product> products, @Argument ReviewFilter filter,
			@Argument Integer first, @Argument String after) {
		List<String> productIds = products.stream().map(Product::id).toList();
		Map<String, List<Review>> reviewsByProductId = reviewService.findByProductIds(productIds, filter);
		return products.stream().collect(Collectors.toMap(product -> product, product -> CursorPagination
				.paginate(reviewsByProductId.getOrDefault(product.id(), List.of()), first, after)));
	}

	/**
	 * Mutation — adds a review to a product and publishes it to {@link #reviewAdded} subscribers.
	 */
	@MutationMapping
	@PreAuthorize("isAuthenticated()")
	public Review addReview(@Argument AddReviewInput input) {
		log.info("[addReview] productId={} author={} rating={}", input.productId(), input.author(), input.rating());
		if (productCatalogService.findProduct(input.productId()).isEmpty()) {
			throw new IllegalArgumentException("Unknown product: " + input.productId());
		}
		return reviewService.addReview(input.productId(), input.author(), input.rating(), input.comment());
	}

	/**
	 * Mutation — ADMIN-only. The one action where USER and ADMIN behave differently; every other operation in this demo
	 * either requires no role or just "logged in."
	 */
	@MutationMapping
	@PreAuthorize("hasRole('ADMIN')")
	public boolean deleteReview(@Argument String id) {
		log.info("[deleteReview] reviewId={}", id);
		return reviewService.deleteReview(id);
	}

	/**
	 * Subscription — streams every review added from this point on, optionally filtered to one product.
	 */
	@SubscriptionMapping
	@PreAuthorize("isAuthenticated()")
	public Flux<Review> reviewAdded(@Argument String productId) {
		log.info("[reviewAdded] subscription opened, productId={}", productId);
		Flux<Review> stream = reviewService.reviewAdded();
		return productId == null ? stream : stream.filter(review -> review.productId().equals(productId));
	}
}
