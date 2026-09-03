package com.testingai.jooq.joins;

import static com.testingai.jooq.generated.Tables.CATEGORY;
import static com.testingai.jooq.generated.Tables.PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jooq.JooqTest;
import org.springframework.context.annotation.Import;

@JooqTest
@Import(CatalogQueryService.class)
class CatalogQueryServiceTest {

	@Autowired
	private DSLContext ctx;

	@Autowired
	private CatalogQueryService catalogQueryService;

	private Long booksId;
	private Long musicId;

	@BeforeEach
	void seedCatalog() {
		booksId = ctx.insertInto(CATEGORY, CATEGORY.NAME).values("Books").returning(CATEGORY.ID).fetchOne().getId();
		musicId = ctx.insertInto(CATEGORY, CATEGORY.NAME).values("Music").returning(CATEGORY.ID).fetchOne().getId();

		ctx.insertInto(PRODUCT, PRODUCT.CATEGORY_ID, PRODUCT.NAME, PRODUCT.PRICE, PRODUCT.STOCK)
				.values(booksId, "Refactoring", new BigDecimal("40.00"), 5).execute();
		ctx.insertInto(PRODUCT, PRODUCT.CATEGORY_ID, PRODUCT.NAME, PRODUCT.PRICE, PRODUCT.STOCK)
				.values(booksId, "Domain-Driven Design", new BigDecimal("60.00"), 3).execute();
		ctx.insertInto(PRODUCT, PRODUCT.CATEGORY_ID, PRODUCT.NAME, PRODUCT.PRICE, PRODUCT.STOCK)
				.values(musicId, "Vinyl Record", new BigDecimal("25.00"), 20).execute();
	}

	@Test
	void listsProductsJoinedWithCategoryName() {
		var products = catalogQueryService.listProductsWithCategory();

		assertThat(products).hasSize(3);
		assertThat(products).extracting(ProductWithCategory::categoryName).containsExactlyInAnyOrder("Books", "Books",
				"Music");
	}

	@Test
	void summarizesEachCategory() {
		var summaries = catalogQueryService.categorySummary();

		var books = summaries.stream().filter(s -> s.categoryId().equals(booksId)).findFirst().orElseThrow();
		assertThat(books.productCount()).isEqualTo(2);
		assertThat(books.totalStock()).isEqualTo(8);
		assertThat(books.avgPrice()).isEqualByComparingTo("50.00");
	}

	@Test
	void ranksProductsByPriceWithinEachCategory() {
		var ranked = catalogQueryService.rankProductsByPriceWithinCategory();

		var topBook = ranked.stream().filter(r -> r.categoryId().equals(booksId) && r.rank() == 1).findFirst()
				.orElseThrow();
		assertThat(topBook.name()).isEqualTo("Domain-Driven Design");
	}
}
