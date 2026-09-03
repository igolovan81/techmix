package com.testingai.jooq.batch;

import static com.testingai.jooq.generated.Tables.CATEGORY;
import static com.testingai.jooq.generated.Tables.CUSTOMER;
import static com.testingai.jooq.generated.Tables.ORDERS;
import static com.testingai.jooq.generated.Tables.ORDER_ITEM;
import static com.testingai.jooq.generated.Tables.PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jooq.JooqTest;
import org.springframework.context.annotation.Import;

@JooqTest
@Import(OrderItemBatchService.class)
class OrderItemBatchServiceTest {

	@Autowired
	private DSLContext ctx;

	@Autowired
	private OrderItemBatchService orderItemBatchService;

	@Test
	void insertsMultipleOrderItemsInOneBatch() {
		Long categoryId = ctx.insertInto(CATEGORY, CATEGORY.NAME).values("Books").returning(CATEGORY.ID).fetchOne()
				.getId();
		Long customerId = ctx.insertInto(CUSTOMER, CUSTOMER.NAME, CUSTOMER.EMAIL)
				.values("Ada Lovelace", "ada@example.com").returning(CUSTOMER.ID).fetchOne().getId();
		Long productAId = ctx.insertInto(PRODUCT, PRODUCT.CATEGORY_ID, PRODUCT.NAME, PRODUCT.PRICE, PRODUCT.STOCK)
				.values(categoryId, "Refactoring", new BigDecimal("40.00"), 5).returning(PRODUCT.ID).fetchOne().getId();
		Long productBId = ctx.insertInto(PRODUCT, PRODUCT.CATEGORY_ID, PRODUCT.NAME, PRODUCT.PRICE, PRODUCT.STOCK)
				.values(categoryId, "Clean Code", new BigDecimal("35.00"), 12).returning(PRODUCT.ID).fetchOne().getId();
		Long orderId = ctx.insertInto(ORDERS, ORDERS.CUSTOMER_ID, ORDERS.PLACED_AT)
				.values(customerId, LocalDateTime.now()).returning(ORDERS.ID).fetchOne().getId();

		int inserted = orderItemBatchService.addItemsBatch(orderId,
				List.of(new BatchOrderItemRequest(productAId, 1, new BigDecimal("40.00")),
						new BatchOrderItemRequest(productBId, 3, new BigDecimal("35.00"))));

		assertThat(inserted).isEqualTo(2);
		assertThat(ctx.selectCount().from(ORDER_ITEM).where(ORDER_ITEM.ORDER_ID.eq(orderId)).fetchOne(0, int.class))
				.isEqualTo(2);
	}
}
