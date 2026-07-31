package com.testingai.graphql.repository;

import com.testingai.graphql.domain.ProductFilter;
import com.testingai.graphql.entity.ProductEntity;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class ProductSpecifications {

	private ProductSpecifications() {
	}

	public static Specification<ProductEntity> matching(ProductFilter filter) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (filter != null) {
				if (filter.nameContains() != null) {
					predicates
							.add(cb.like(cb.lower(root.get("name")), "%" + filter.nameContains().toLowerCase() + "%"));
				}
				if (filter.minPriceCents() != null) {
					predicates.add(cb.greaterThanOrEqualTo(root.get("priceCents"), filter.minPriceCents().longValue()));
				}
				if (filter.maxPriceCents() != null) {
					predicates.add(cb.lessThanOrEqualTo(root.get("priceCents"), filter.maxPriceCents().longValue()));
				}
			}
			return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
		};
	}

	public static Specification<ProductEntity> idAfter(Long cursorId) {
		return (root, query, cb) -> cursorId == null ? cb.conjunction() : cb.greaterThan(root.get("id"), cursorId);
	}

	public static Specification<ProductEntity> inCategory(Long categoryId) {
		return (root, query, cb) -> {
			query.distinct(true);
			return cb.equal(root.join("categories").get("id"), categoryId);
		};
	}
}
