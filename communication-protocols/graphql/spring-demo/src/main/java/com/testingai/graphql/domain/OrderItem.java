package com.testingai.graphql.domain;

public record OrderItem(Long id, Long orderId, Long productId, int quantity, long unitPriceCents) {
	public long lineTotalCents() {
		return (long) quantity * unitPriceCents;
	}
}
