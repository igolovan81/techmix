package com.testingai.graphql.controller;

import com.testingai.graphql.domain.Product;
import com.testingai.graphql.domain.ProductCatalogService;
import com.testingai.graphql.domain.Review;
import com.testingai.graphql.domain.ReviewService;
import com.testingai.graphql.util.FailureSimulator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

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
	 * Query — returns the full in-memory catalog.
	 */
	@QueryMapping
	public List<Product> products() {
		log.info("[products] returning {} products", productCatalogService.listProducts().size());
		return productCatalogService.listProducts();
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
	 * {@link ReviewService#findByProductIds(List)} instead of once per product (the N+1 problem).
	 */
	@BatchMapping
	public Map<Product, List<Review>> reviews(List<Product> products) {
		List<String> productIds = products.stream().map(Product::id).toList();
		Map<String, List<Review>> reviewsByProductId = reviewService.findByProductIds(productIds);
		return products.stream().collect(Collectors.toMap(product -> product,
				product -> reviewsByProductId.getOrDefault(product.id(), List.of())));
	}
}
