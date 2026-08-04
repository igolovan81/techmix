package com.testingai.websockets.domain;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderTrackingService {

	private static final Map<OrderStatus, OrderStatus> NEXT_STATUS = Map.of(OrderStatus.CREATED, OrderStatus.PAID,
			OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

	private final Map<String, Order> orders = new ConcurrentHashMap<>();
	private final List<OrderEventPublisher> publishers;

	public OrderTrackingService(List<OrderEventPublisher> publishers) {
		this.publishers = publishers;
	}

	public Order create() {
		Order order = new Order(UUID.randomUUID().toString(), OrderStatus.CREATED, Instant.now());
		orders.put(order.id(), order);
		return order;
	}

	public Order advance(String orderId) {
		Order current = get(orderId);
		OrderStatus next = NEXT_STATUS.get(current.status());
		if (next == null) {
			throw new NoNextStatusException(orderId, current.status());
		}
		Order updated = current.withStatus(next, Instant.now());
		orders.put(orderId, updated);
		publishers.forEach(publisher -> publisher.publish(new OrderEvent(orderId, next, updated.updatedAt())));
		return updated;
	}

	public Order get(String orderId) {
		Order order = orders.get(orderId);
		if (order == null) {
			throw new OrderNotFoundException(orderId);
		}
		return order;
	}
}
