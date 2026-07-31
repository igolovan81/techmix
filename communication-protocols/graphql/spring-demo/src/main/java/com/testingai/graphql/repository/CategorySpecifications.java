package com.testingai.graphql.repository;

import com.testingai.graphql.entity.CategoryEntity;
import org.springframework.data.jpa.domain.Specification;

public final class CategorySpecifications {

	private CategorySpecifications() {
	}

	public static Specification<CategoryEntity> idAfter(Long cursorId) {
		return (root, query, cb) -> cursorId == null ? cb.conjunction() : cb.greaterThan(root.get("id"), cursorId);
	}
}
