package com.testingai.jooq.joins;

import static com.testingai.jooq.generated.Tables.CATEGORY;
import static com.testingai.jooq.generated.Tables.PRODUCT;
import static org.jooq.impl.DSL.avg;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.partitionBy;
import static org.jooq.impl.DSL.rank;
import static org.jooq.impl.DSL.sum;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CatalogQueryService {

	private final DSLContext ctx;

	public List<ProductWithCategory> listProductsWithCategory() {
		return ctx.select(PRODUCT.ID, PRODUCT.NAME, PRODUCT.PRICE, CATEGORY.NAME).from(PRODUCT).join(CATEGORY)
				.on(PRODUCT.CATEGORY_ID.eq(CATEGORY.ID)).orderBy(PRODUCT.ID)
				.fetch(r -> new ProductWithCategory(r.value1(), r.value2(), r.value3(), r.value4()));
	}

	public List<CategorySummary> categorySummary() {
		return ctx.select(CATEGORY.ID, CATEGORY.NAME, count(PRODUCT.ID), sum(PRODUCT.STOCK), avg(PRODUCT.PRICE))
				.from(CATEGORY).leftJoin(PRODUCT).on(PRODUCT.CATEGORY_ID.eq(CATEGORY.ID))
				.groupBy(CATEGORY.ID, CATEGORY.NAME).orderBy(CATEGORY.ID).fetch(r -> new CategorySummary(r.value1(),
						r.value2(), r.value3(), r.value4() == null ? 0 : r.value4().intValue(), r.value5()));
	}

	public List<RankedProduct> rankProductsByPriceWithinCategory() {
		return ctx
				.select(PRODUCT.ID, PRODUCT.NAME, PRODUCT.PRICE, PRODUCT.CATEGORY_ID,
						rank().over(partitionBy(PRODUCT.CATEGORY_ID).orderBy(PRODUCT.PRICE.desc())))
				.from(PRODUCT).orderBy(PRODUCT.CATEGORY_ID, PRODUCT.PRICE.desc())
				.fetch(r -> new RankedProduct(r.value1(), r.value2(), r.value3(), r.value4(), r.value5()));
	}
}
