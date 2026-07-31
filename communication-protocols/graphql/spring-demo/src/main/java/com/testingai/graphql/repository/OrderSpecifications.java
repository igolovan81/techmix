package com.testingai.graphql.repository;

import com.testingai.graphql.domain.OrderStatus;
import com.testingai.graphql.entity.OrderEntity;
import org.springframework.data.jpa.domain.Specification;

public final class OrderSpecifications {

	private OrderSpecifications() {
	}

	public static Specification<OrderEntity> matchingStatus(OrderStatus status) {
		return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
	}

	public static Specification<OrderEntity> idAfter(Long cursorId) {
		return (root, query, cb) -> cursorId == null ? cb.conjunction() : cb.greaterThan(root.get("id"), cursorId);
	}
}
