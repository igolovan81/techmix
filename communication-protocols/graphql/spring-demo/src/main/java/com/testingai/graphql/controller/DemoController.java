package com.testingai.graphql.controller;

import com.testingai.graphql.domain.AddReviewInput;
import com.testingai.graphql.domain.Category;
import com.testingai.graphql.domain.CategoryService;
import com.testingai.graphql.domain.Order;
import com.testingai.graphql.domain.OrderItem;
import com.testingai.graphql.domain.OrderService;
import com.testingai.graphql.domain.OrderStatus;
import com.testingai.graphql.domain.PlaceOrderInput;
import com.testingai.graphql.domain.Product;
import com.testingai.graphql.domain.ProductCatalogService;
import com.testingai.graphql.domain.ProductFilter;
import com.testingai.graphql.domain.Review;
import com.testingai.graphql.domain.ReviewFilter;
import com.testingai.graphql.domain.ReviewService;
import com.testingai.graphql.domain.Role;
import com.testingai.graphql.domain.User;
import com.testingai.graphql.domain.UserService;
import com.testingai.graphql.pagination.Connection;
import com.testingai.graphql.pagination.CursorPagination;
import com.testingai.graphql.util.FailureSimulator;
import graphql.schema.DataFetchingEnvironment;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dataloader.DataLoader;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
	private final UserService userService;
	private final CategoryService categoryService;
	private final OrderService orderService;
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
	 * Registers the "categoryChildren" DataLoader — same idiom as {@link #registerReviewsBatchLoader()}: needed because
	 * {@link #categoryChildren} takes pagination {@code @Argument}s that a {@code @BatchMapping} method can't accept.
	 * Cheap regardless of overall table size since only ~100 categories exist in total.
	 */
	@PostConstruct
	void registerCategoryChildrenBatchLoader() {
		batchLoaderRegistry.<Long, List<Category>>forName("categoryChildren").registerMappedBatchLoader((parentIds,
				environment) -> Mono.just(categoryService.findChildrenByParentIds(new ArrayList<>(parentIds))));
	}

	/**
	 * Registers the "userOrders" DataLoader — same idiom as {@link #registerReviewsBatchLoader()}/
	 * {@link #registerCategoryChildrenBatchLoader()}: {@link #userOrders} needs pagination {@code @Argument}s that a
	 * {@code @BatchMapping} method can't accept.
	 */
	@PostConstruct
	void registerUserOrdersBatchLoader() {
		batchLoaderRegistry.<Long, List<Order>>forName("userOrders").registerMappedBatchLoader(
				(userIds, environment) -> Mono.just(orderService.findByUserIds(new ArrayList<>(userIds))));
	}

	/**
	 * Query — returns a filtered, paginated page of the catalog, pushed down to the database via keyset pagination (see
	 * {@link com.testingai.graphql.pagination.KeysetPagination}) rather than loading the full table.
	 */
	@QueryMapping
	public Connection<Product> products(@Argument ProductFilter filter, @Argument Integer first,
			@Argument String after) {
		Connection<Product> page = productCatalogService.listProducts(filter, first, after);
		log.info("[products] returning {} of {} total products", page.edges().size(), page.totalCount());
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

	@QueryMapping
	public Connection<Category> categories(@Argument Integer first, @Argument String after) {
		return categoryService.listCategories(first, after);
	}

	@QueryMapping
	public Category category(@Argument Long id) {
		return categoryService.findCategory(id).orElse(null);
	}

	/**
	 * Batch mapping for {@code Product.categories} — no {@code @Argument} needed, so this uses {@code @BatchMapping}
	 * directly (explicit {@code typeName}/{@code field} since the method name intentionally doesn't match either the
	 * {@code Query.categories} query above or the schema field, to keep the two unambiguous to a reader).
	 */
	@BatchMapping(typeName = "Product", field = "categories")
	public Map<Product, List<Category>> productCategories(List<Product> products) {
		Map<String, List<Category>> byProductId = productCatalogService
				.findCategoriesByProductIds(products.stream().map(Product::id).toList());
		Map<Product, List<Category>> result = new LinkedHashMap<>();
		for (Product product : products) {
			result.put(product, byProductId.getOrDefault(product.id(), List.of()));
		}
		return result;
	}

	@BatchMapping(typeName = "Category", field = "parent")
	public Map<Category, Category> categoryParent(List<Category> categories) {
		List<Long> parentIds = categories.stream().map(Category::parentId).filter(Objects::nonNull).distinct().toList();
		Map<Long, Category> byId = categoryService.findByIds(parentIds);
		Map<Category, Category> result = new LinkedHashMap<>();
		for (Category category : categories) {
			result.put(category, category.parentId() == null ? null : byId.get(category.parentId()));
		}
		return result;
	}

	@SchemaMapping(typeName = "Category", field = "children")
	public CompletableFuture<Connection<Category>> categoryChildren(Category category, @Argument Integer first,
			@Argument String after, DataFetchingEnvironment environment) {
		DataLoader<Long, List<Category>> loader = environment.getDataLoaderRegistry().getDataLoader("categoryChildren");
		return loader.load(category.id()).thenApply(children -> CursorPagination.paginate(children, first, after));
	}

	/**
	 * Schema mapping for {@code Category.products} — deliberately NOT a DataLoader: batching it the way
	 * {@link #reviews} works would mean loading a category's entire unpaginated product list (potentially thousands of
	 * rows) per key just to slice it in memory afterward, which defeats the point of {@code listProductsInCategory}
	 * pushing pagination down to the database. One small keyset query per category node resolved instead — bounded by
	 * the outer {@code categories(first: ...)} argument, not by table size.
	 */
	@SchemaMapping(typeName = "Category", field = "products")
	public Connection<Product> categoryProducts(Category category, @Argument ProductFilter filter,
			@Argument Integer first, @Argument String after) {
		return productCatalogService.listProductsInCategory(category.id(), filter, first, after);
	}

	/**
	 * Query — the current authenticated user, resolved from the Basic-Auth principal.
	 */
	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	public User me(Principal principal) {
		return userService.findByUsername(principal.getName()).orElseThrow(() -> new IllegalStateException(
				"Authenticated principal has no matching User: " + principal.getName()));
	}

	/**
	 * Query — a single order by id. Row-level (not just role-level) authorization: the resolver loads the order, then
	 * allows it only if the caller is the owning user or an ADMIN, throwing {@link AccessDeniedException} (classified
	 * {@code FORBIDDEN}/{@code UNAUTHORIZED} by the existing {@code DemoExceptionResolver}, no changes needed there)
	 * otherwise — genuinely new coverage vs. every other check in this module, which is role-only.
	 */
	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	public Order order(@Argument Long id, Principal principal) {
		Order order = orderService.findById(id).orElse(null);
		if (order == null) {
			return null;
		}
		User caller = userService.findByUsername(principal.getName()).orElseThrow(() -> new IllegalStateException(
				"Authenticated principal has no matching User: " + principal.getName()));
		boolean isOwner = order.userId().equals(caller.id());
		boolean isAdmin = caller.role() == Role.ADMIN;
		if (!isOwner && !isAdmin) {
			throw new AccessDeniedException("Not authorized to view order " + id);
		}
		return order;
	}

	/**
	 * Query — ADMIN-only browse-all, with DB-pushed-down keyset pagination (same reasoning as {@code products}).
	 */
	@QueryMapping
	@PreAuthorize("hasRole('ADMIN')")
	public Connection<Order> orders(@Argument OrderStatus status, @Argument Integer first, @Argument String after) {
		return orderService.listOrders(status, first, after);
	}

	@BatchMapping(typeName = "Order", field = "user")
	public Map<Order, User> orderUser(List<Order> orders) {
		Map<Long, User> byId = userService.findByIds(orders.stream().map(Order::userId).distinct().toList());
		Map<Order, User> result = new LinkedHashMap<>();
		for (Order order : orders) {
			result.put(order, byId.get(order.userId()));
		}
		return result;
	}

	@BatchMapping(typeName = "OrderItem", field = "product")
	public Map<OrderItem, Product> orderItemProduct(List<OrderItem> orderItems) {
		Map<String, Product> byId = productCatalogService
				.findByIds(orderItems.stream().map(item -> item.productId().toString()).distinct().toList());
		Map<OrderItem, Product> result = new LinkedHashMap<>();
		for (OrderItem item : orderItems) {
			result.put(item, byId.get(item.productId().toString()));
		}
		return result;
	}

	@BatchMapping(typeName = "Order", field = "items")
	public Map<Order, List<OrderItem>> orderItems(List<Order> orders) {
		Map<Long, List<OrderItem>> byOrderId = orderService
				.findItemsByOrderIds(orders.stream().map(Order::id).toList());
		Map<Order, List<OrderItem>> result = new LinkedHashMap<>();
		for (Order order : orders) {
			result.put(order, byOrderId.getOrDefault(order.id(), List.of()));
		}
		return result;
	}

	@SchemaMapping(typeName = "User", field = "orders")
	public CompletableFuture<Connection<Order>> userOrders(User user, @Argument Integer first, @Argument String after,
			DataFetchingEnvironment environment) {
		DataLoader<Long, List<Order>> loader = environment.getDataLoaderRegistry().getDataLoader("userOrders");
		return loader.load(user.id()).thenApply(orders -> CursorPagination.paginate(orders, first, after));
	}

	/**
	 * Mutation — places an order for the authenticated principal, resolved to a domain {@link User}. Business logic
	 * (stock validation, price snapshot, transactional rollback on any line failing) lives in
	 * {@link OrderService#placeOrder}.
	 */
	@MutationMapping
	@PreAuthorize("isAuthenticated()")
	public Order placeOrder(@Argument PlaceOrderInput input, Principal principal) {
		log.info("[placeOrder] username={} itemCount={}", principal.getName(), input.items().size());
		return orderService.placeOrder(principal.getName(), input.items());
	}

	/**
	 * Mutation — ADMIN-only, same pattern as {@link #deleteReview}.
	 */
	@MutationMapping
	@PreAuthorize("hasRole('ADMIN')")
	public Order updateOrderStatus(@Argument Long id, @Argument OrderStatus status) {
		log.info("[updateOrderStatus] orderId={} status={}", id, status);
		return orderService.updateOrderStatus(id, status);
	}

	/**
	 * Mutation — adds a review to a product and publishes it to {@link #reviewAdded} subscribers. The author is the
	 * authenticated principal, resolved to a domain {@link User}, not a caller-supplied value.
	 */
	@MutationMapping
	@PreAuthorize("isAuthenticated()")
	public Review addReview(@Argument AddReviewInput input, Principal principal) {
		log.info("[addReview] productId={} username={} rating={}", input.productId(), principal.getName(),
				input.rating());
		Long authorId = userService.findByUsername(principal.getName()).orElseThrow(
				() -> new IllegalStateException("Authenticated principal has no matching User: " + principal.getName()))
				.id();
		return reviewService.addReview(input.productId(), authorId, input.rating(), input.comment());
	}

	/**
	 * Batch mapping for {@code Review.author} — no {@code @Argument} is needed here (unlike {@link #reviews}), so
	 * unlike the manual {@code DataLoader} registered in {@link #registerReviewsBatchLoader()}, this can use
	 * {@code @BatchMapping} directly and let Spring GraphQL register the batch loader itself.
	 */
	@BatchMapping
	public Map<Review, User> author(List<Review> reviews) {
		Map<Long, User> byId = userService.findByIds(reviews.stream().map(Review::authorId).distinct().toList());
		Map<Review, User> result = new LinkedHashMap<>();
		for (Review review : reviews) {
			result.put(review, byId.get(review.authorId()));
		}
		return result;
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
