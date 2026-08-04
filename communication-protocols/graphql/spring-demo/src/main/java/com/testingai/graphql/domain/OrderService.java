package com.testingai.graphql.domain;

import com.testingai.graphql.entity.OrderEntity;
import com.testingai.graphql.entity.OrderItemEntity;
import com.testingai.graphql.entity.ProductEntity;
import com.testingai.graphql.entity.UserEntity;
import com.testingai.graphql.exception.InsufficientStockException;
import com.testingai.graphql.pagination.Connection;
import com.testingai.graphql.pagination.KeysetPagination;
import com.testingai.graphql.repository.OrderItemRepository;
import com.testingai.graphql.repository.OrderRepository;
import com.testingai.graphql.repository.OrderSpecifications;
import com.testingai.graphql.repository.ProductRepository;
import com.testingai.graphql.repository.UserRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final ProductRepository productRepository;
	private final UserRepository userRepository;

	public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
			ProductRepository productRepository, UserRepository userRepository) {
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.productRepository = productRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public Order placeOrder(String username, List<OrderItemInput> items) {
		UserEntity user = userRepository.findByUsername(username)
				.orElseThrow(() -> new NoSuchElementException("Unknown user: " + username));

		OrderEntity order = new OrderEntity();
		order.setUser(user);
		order.setStatus(OrderStatus.PENDING);
		order.setPlacedAt(Instant.now());

		for (OrderItemInput itemInput : items) {
			Long productId = Long.parseLong(itemInput.productId());
			// Pessimistic write lock: two concurrent placeOrder calls against the same product must not both read
			// the same stockQty and both succeed a decrement that oversells it.
			ProductEntity product = productRepository.findByIdForUpdate(productId)
					.orElseThrow(() -> new IllegalArgumentException("Unknown product: " + itemInput.productId()));
			if (product.getStockQty() < itemInput.quantity()) {
				throw new InsufficientStockException("Insufficient stock for product " + itemInput.productId()
						+ ": requested " + itemInput.quantity() + ", available " + product.getStockQty());
			}
			product.setStockQty(product.getStockQty() - itemInput.quantity());

			OrderItemEntity item = new OrderItemEntity();
			item.setOrder(order);
			item.setProduct(product);
			item.setQuantity(itemInput.quantity());
			item.setUnitPriceCents(product.getPriceCents());
			order.getItems().add(item);
		}

		return toOrder(orderRepository.save(order));
	}

	@Transactional
	public Order updateOrderStatus(Long orderId, OrderStatus status) {
		OrderEntity order = orderRepository.findById(orderId)
				.orElseThrow(() -> new NoSuchElementException("Unknown order: " + orderId));
		order.setStatus(status);
		return toOrder(order);
	}

	// readOnly: toOrder() below reads the lazy `items` collection to compute totalCents — unlike placeOrder, where
	// the OrderEntity is a brand-new in-memory object with items already added to a plain list, these entities are
	// freshly loaded from the DB, so items really is an uninitialized Hibernate proxy that needs an open session.
	@Transactional(readOnly = true)
	public Map<Long, List<Order>> findByUserIds(List<Long> userIds) {
		Map<Long, List<Order>> byUserId = orderRepository
				.findAll((root, query, cb) -> root.get("user").get("id").in(userIds)).stream()
				.collect(Collectors.groupingBy(o -> o.getUser().getId(),
						Collectors.mapping(OrderService::toOrder, Collectors.toList())));
		Map<Long, List<Order>> result = new LinkedHashMap<>();
		for (Long userId : userIds) {
			result.put(userId, byUserId.getOrDefault(userId, List.of()));
		}
		return result;
	}

	public Map<Long, List<OrderItem>> findItemsByOrderIds(List<Long> orderIds) {
		Map<Long, List<OrderItem>> byOrderId = orderItemRepository.findByOrderIdIn(orderIds).stream()
				.map(OrderService::toOrderItem).collect(Collectors.groupingBy(OrderItem::orderId));
		Map<Long, List<OrderItem>> result = new LinkedHashMap<>();
		for (Long orderId : orderIds) {
			result.put(orderId, byOrderId.getOrDefault(orderId, List.of()));
		}
		return result;
	}

	@Transactional(readOnly = true)
	public Connection<Order> listOrders(OrderStatus status, Integer first, String after) {
		Long cursorId = KeysetPagination.decodeCursor(after);
		int limit = KeysetPagination.normalizeFirst(first);
		var spec = OrderSpecifications.matchingStatus(status).and(OrderSpecifications.idAfter(cursorId));

		List<OrderEntity> rows = orderRepository.findAll(spec, PageRequest.of(0, limit + 1, Sort.by("id")))
				.getContent();
		return KeysetPagination.paginate(rows, limit, OrderEntity::getId, OrderService::toOrder,
				orderRepository.count(OrderSpecifications.matchingStatus(status)));
	}

	@Transactional(readOnly = true)
	public Optional<Order> findById(Long id) {
		return orderRepository.findById(id).map(OrderService::toOrder);
	}

	static OrderItem toOrderItem(OrderItemEntity entity) {
		return new OrderItem(entity.getId(), entity.getOrder().getId(), entity.getProduct().getId(),
				entity.getQuantity(), entity.getUnitPriceCents());
	}

	static Order toOrder(OrderEntity entity) {
		long totalCents = entity.getItems().stream().mapToLong(i -> (long) i.getQuantity() * i.getUnitPriceCents())
				.sum();
		return new Order(entity.getId(), entity.getUser().getId(), entity.getStatus(), entity.getPlacedAt().toString(),
				totalCents);
	}
}
