package com.testingai.jooq.search;

import static com.testingai.jooq.generated.Tables.CATEGORY;
import static com.testingai.jooq.generated.Tables.PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.jooq.dsl.ProductView;
import java.math.BigDecimal;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jooq.JooqTest;
import org.springframework.context.annotation.Import;

@JooqTest
@Import(ProductSearchService.class)
class ProductSearchServiceTest {

	@Autowired
	private DSLContext ctx;

	@Autowired
	private ProductSearchService productSearchService;

	private Long categoryId;

	@BeforeEach
	void seedCatalog() {
		categoryId = ctx.insertInto(CATEGORY, CATEGORY.NAME).values("Books").returning(CATEGORY.ID).fetchOne().getId();

		ctx.insertInto(PRODUCT, PRODUCT.CATEGORY_ID, PRODUCT.NAME, PRODUCT.PRICE, PRODUCT.STOCK)
				.values(categoryId, "Refactoring", new BigDecimal("40.00"), 5).execute();
		ctx.insertInto(PRODUCT, PRODUCT.CATEGORY_ID, PRODUCT.NAME, PRODUCT.PRICE, PRODUCT.STOCK)
				.values(categoryId, "Domain-Driven Design", new BigDecimal("60.00"), 0).execute();
		ctx.insertInto(PRODUCT, PRODUCT.CATEGORY_ID, PRODUCT.NAME, PRODUCT.PRICE, PRODUCT.STOCK)
				.values(categoryId, "Clean Code", new BigDecimal("35.00"), 12).execute();
	}

	@Test
	void returnsEverythingWhenNoFilterIsSet() {
		var results = productSearchService.search(new ProductSearchCriteria(null, null, null, null, null));
		assertThat(results).hasSize(3);
	}

	@Test
	void combinesPriceRangeAndInStockFilters() {
		var results = productSearchService
				.search(new ProductSearchCriteria(null, new BigDecimal("30.00"), new BigDecimal("50.00"), true, null));

		assertThat(results).extracting(ProductView::name).containsExactly("Refactoring", "Clean Code");
	}

	@Test
	void filtersByNameSubstringCaseInsensitively() {
		var results = productSearchService.search(new ProductSearchCriteria(null, null, null, null, "domain"));

		assertThat(results).extracting(ProductView::name).containsExactly("Domain-Driven Design");
	}
}
