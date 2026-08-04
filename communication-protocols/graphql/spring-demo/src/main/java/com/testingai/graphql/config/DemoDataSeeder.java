package com.testingai.graphql.config;

import com.testingai.graphql.domain.OrderStatus;
import com.testingai.graphql.domain.Role;
import com.testingai.graphql.entity.CategoryEntity;
import com.testingai.graphql.entity.OrderEntity;
import com.testingai.graphql.entity.OrderItemEntity;
import com.testingai.graphql.entity.ProductEntity;
import com.testingai.graphql.entity.ReviewEntity;
import com.testingai.graphql.entity.UserEntity;
import com.testingai.graphql.repository.CategoryRepository;
import com.testingai.graphql.repository.OrderRepository;
import com.testingai.graphql.repository.ProductRepository;
import com.testingai.graphql.repository.ReviewRepository;
import com.testingai.graphql.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic (fixed-seed {@link Random}) demo data generator — volumes come from {@link SeedProperties}, not
 * hardcoded, so the test profile (small) and default profile (10k products) share this exact class. Guarded by
 * {@code userRepository.count() == 0} so re-running the app against an already-populated database is a no-op.
 */
@Slf4j
@Component
public class DemoDataSeeder implements ApplicationRunner {

	private static final long SEED = 42L;
	private static final List<String> PRODUCT_NAMES = List.of("Widget", "Gadget", "Gizmo", "Doohickey", "Thingamajig",
			"Contraption", "Doodad", "Whatsit", "Gizmotron", "Thingamabob");
	private static final List<String> PRODUCT_VARIANTS = List.of("Mini", "Standard", "Pro", "Max");
	private static final List<String> CATEGORY_NAMES = List.of("Electronics", "Audio", "Home", "Kitchen", "Outdoors",
			"Office", "Sports", "Toys", "Books", "Garden");

	private final UserRepository userRepository;
	private final CategoryRepository categoryRepository;
	private final ProductRepository productRepository;
	private final ReviewRepository reviewRepository;
	private final OrderRepository orderRepository;
	private final SeedProperties seedProperties;
	private final Random random = new Random(SEED);

	public DemoDataSeeder(UserRepository userRepository, CategoryRepository categoryRepository,
			ProductRepository productRepository, ReviewRepository reviewRepository, OrderRepository orderRepository,
			SeedProperties seedProperties) {
		this.userRepository = userRepository;
		this.categoryRepository = categoryRepository;
		this.productRepository = productRepository;
		this.reviewRepository = reviewRepository;
		this.orderRepository = orderRepository;
		this.seedProperties = seedProperties;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!seedProperties.enabled() || userRepository.count() > 0) {
			log.info("[seed] skipped: seeding disabled or data already present");
			return;
		}
		List<UserEntity> users = seedUsers();
		List<CategoryEntity> categories = seedCategories();
		List<ProductEntity> products = seedProducts(categories);
		seedReviews(products, users);
		seedOrders(users, products);
		log.info("[seed] complete: {} users, {} categories, {} products", users.size(), categories.size(),
				products.size());
	}

	private List<UserEntity> seedUsers() {
		List<UserEntity> users = new ArrayList<>();
		users.add(newUser("user", "user@example.com", "Demo User", Role.CUSTOMER));
		users.add(newUser("admin", "admin@example.com", "Demo Admin", Role.ADMIN));
		for (int i = users.size(); i < seedProperties.userCount(); i++) {
			users.add(newUser("customer" + i, "customer" + i + "@example.com", "Customer " + i, Role.CUSTOMER));
		}
		return userRepository.saveAll(users);
	}

	private static UserEntity newUser(String username, String email, String displayName, Role role) {
		UserEntity user = new UserEntity();
		user.setUsername(username);
		user.setEmail(email);
		user.setDisplayName(displayName);
		user.setRole(role);
		return user;
	}

	private List<CategoryEntity> seedCategories() {
		List<CategoryEntity> roots = new ArrayList<>();
		for (String name : CATEGORY_NAMES) {
			CategoryEntity root = new CategoryEntity();
			root.setName(name);
			roots.add(root);
		}
		roots = categoryRepository.saveAll(roots);

		List<CategoryEntity> children = new ArrayList<>();
		int remaining = seedProperties.categoryCount() - roots.size();
		for (int i = 0; i < Math.max(0, remaining); i++) {
			CategoryEntity parent = roots.get(random.nextInt(roots.size()));
			CategoryEntity child = new CategoryEntity();
			child.setName(parent.getName() + " " + (i + 1));
			child.setParent(parent);
			children.add(child);
		}
		children = categoryRepository.saveAll(children);

		List<CategoryEntity> all = new ArrayList<>(roots);
		all.addAll(children);
		return all;
	}

	private List<ProductEntity> seedProducts(List<CategoryEntity> categories) {
		List<ProductEntity> products = new ArrayList<>();
		for (int i = 0; i < seedProperties.productCount(); i++) {
			String variant = PRODUCT_VARIANTS.get(random.nextInt(PRODUCT_VARIANTS.size()));
			String name = PRODUCT_NAMES.get(random.nextInt(PRODUCT_NAMES.size()));
			ProductEntity product = new ProductEntity();
			product.setName(variant + " " + name + " #" + (i + 1));
			product.setPriceCents(499 + random.nextInt(9500));
			product.setStockQty(10 + random.nextInt(200));
			int categoryCount = 1 + random.nextInt(3);
			for (int c = 0; c < categoryCount; c++) {
				product.getCategories().add(categories.get(random.nextInt(categories.size())));
			}
			products.add(product);
		}
		return productRepository.saveAll(products);
	}

	private void seedReviews(List<ProductEntity> products, List<UserEntity> users) {
		List<ReviewEntity> reviews = new ArrayList<>();
		int span = seedProperties.maxReviewsPerProduct() - seedProperties.minReviewsPerProduct() + 1;
		for (ProductEntity product : products) {
			int count = seedProperties.minReviewsPerProduct() + (span > 0 ? random.nextInt(span) : 0);
			for (int i = 0; i < count; i++) {
				ReviewEntity review = new ReviewEntity();
				review.setProduct(product);
				review.setAuthor(users.get(random.nextInt(users.size())));
				review.setRating(1 + random.nextInt(5));
				review.setComment("Review #" + (i + 1) + " of " + product.getName());
				reviews.add(review);
			}
		}
		reviewRepository.saveAll(reviews);
	}

	private void seedOrders(List<UserEntity> users, List<ProductEntity> products) {
		List<OrderEntity> orders = new ArrayList<>();
		OrderStatus[] statuses = OrderStatus.values();
		for (int i = 0; i < seedProperties.orderCount(); i++) {
			OrderEntity order = new OrderEntity();
			order.setUser(users.get(random.nextInt(users.size())));
			order.setStatus(statuses[random.nextInt(statuses.length)]);
			order.setPlacedAt(Instant.now().minusSeconds(random.nextInt(60 * 60 * 24 * 90)));

			int lineCount = 1 + random.nextInt(5);
			for (int l = 0; l < lineCount; l++) {
				ProductEntity product = products.get(random.nextInt(products.size()));
				OrderItemEntity item = new OrderItemEntity();
				item.setOrder(order);
				item.setProduct(product);
				item.setQuantity(1 + random.nextInt(3));
				item.setUnitPriceCents(product.getPriceCents());
				order.getItems().add(item);
				product.setStockQty(Math.max(0, product.getStockQty() - item.getQuantity()));
			}
			orders.add(order);
		}
		orderRepository.saveAll(orders);
	}
}
