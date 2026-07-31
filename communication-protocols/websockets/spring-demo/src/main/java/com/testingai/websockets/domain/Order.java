package com.testingai.websockets.domain;

import java.time.Instant;

public record Order(String id, OrderStatus status, Instant updatedAt) {

	public Order withStatus(OrderStatus newStatus, Instant at) {
		return new Order(id, newStatus, at);
	}
}
