package com.testingai.jooq.search;

import static com.testingai.jooq.generated.Tables.PRODUCT;

import com.testingai.jooq.dsl.ProductView;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductSearchService {

	private final DSLContext ctx;

	public List<ProductView> search(ProductSearchCriteria criteria) {
		List<Condition> conditions = new ArrayList<>();
		if (criteria.categoryId() != null) {
			conditions.add(PRODUCT.CATEGORY_ID.eq(criteria.categoryId()));
		}
		if (criteria.minPrice() != null) {
			conditions.add(PRODUCT.PRICE.ge(criteria.minPrice()));
		}
		if (criteria.maxPrice() != null) {
			conditions.add(PRODUCT.PRICE.le(criteria.maxPrice()));
		}
		if (Boolean.TRUE.equals(criteria.inStockOnly())) {
			conditions.add(PRODUCT.STOCK.gt(0));
		}
		if (criteria.nameContains() != null && !criteria.nameContains().isBlank()) {
			conditions.add(PRODUCT.NAME.likeIgnoreCase("%" + criteria.nameContains() + "%"));
		}

		return ctx.selectFrom(PRODUCT).where(DSL.and(conditions)).orderBy(PRODUCT.ID)
				.fetch(r -> new ProductView(r.getId(), r.getCategoryId(), r.getName(), r.getPrice(), r.getStock()));
	}
}
