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
import graphql.schema.DataFetchingEnvironment;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dataloader.DataLoader;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Hosts every GraphQL operation for this demo — queries, the DataLoader-backed {@code reviews} field, the mutation, and
 * the subscription all live here, mirroring how {@code grpc/client-demo}'s {@code DemoController} centralizes every RPC
 * pattern in one class.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class DemoController {

	private final ProductCatalogService productCatalogService;
	private final ReviewService reviewService;
	private final BatchLoaderRegistry batchLoaderRegistry;

	/**
	 * Registers the "reviews" DataLoader — the raw, unfiltered/unpaginated review list per product id, batched into one
	 * {@link ReviewService#findByProductIds(List)} call per query. {@code @BatchMapping} can't be used for
	 * {@code Product.reviews} here because its handler method can't accept {@code @Argument} parameters (verified
	 * against spring-graphql's {@code BatchLoaderHandlerMethod}, which only resolves the keys collection,
	 * {@code @ContextValue}, {@code GraphQLContext}, {@code BatchLoaderEnvironment}, or {@code Principal}) — so
	 * filtering/pagination, which need {@code @Argument}, are applied afterward in {@link #reviews} instead of inside
	 * this batch function.
	 */
	@PostConstruct
	void registerReviewsBatchLoader() {
		batchLoaderRegistry.<String, List<Review>>forName("reviews").registerMappedBatchLoader(
				(productIds, environment) -> Mono.just(reviewService.findByProductIds(new ArrayList<>(productIds))));
	}

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
	 * Schema mapping for {@code Product.reviews} — still one batched call per query for however many products are being
	 * resolved (via the "reviews" DataLoader registered in {@link #registerReviewsBatchLoader()}), but filtering and
	 * pagination are applied here, after the raw list loads, since the batch function itself can't take
	 * {@code @Argument} parameters.
	 */
	@SchemaMapping
	public CompletableFuture<Connection<Review>> reviews(Product product, @Argument ReviewFilter filter,
			@Argument Integer first, @Argument String after, DataFetchingEnvironment environment) {
		DataLoader<String, List<Review>> loader = environment.getDataLoaderRegistry().getDataLoader("reviews");
		return loader.load(product.id()).thenApply(
				reviews -> CursorPagination.paginate(reviewService.filterReviews(reviews, filter), first, after));
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
