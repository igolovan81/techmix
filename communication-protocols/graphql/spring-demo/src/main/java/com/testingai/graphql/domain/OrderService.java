package com.testingai.graphql.domain;

import com.testingai.graphql.entity.OrderEntity;
import com.testingai.graphql.entity.OrderItemEntity;
import com.testingai.graphql.entity.ProductEntity;
import com.testingai.graphql.entity.UserEntity;
import com.testingai.graphql.exception.InsufficientStockException;
import com.testingai.graphql.repository.OrderItemRepository;
import com.testingai.graphql.repository.OrderRepository;
import com.testingai.graphql.repository.ProductRepository;
import com.testingai.graphql.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
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

	static Order toOrder(OrderEntity entity) {
		long totalCents = entity.getItems().stream().mapToLong(i -> (long) i.getQuantity() * i.getUnitPriceCents())
				.sum();
		return new Order(entity.getId(), entity.getUser().getId(), entity.getStatus(), entity.getPlacedAt().toString(),
				totalCents);
	}
}
