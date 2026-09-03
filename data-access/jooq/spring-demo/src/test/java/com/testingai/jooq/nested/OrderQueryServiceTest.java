package com.testingai.jooq.nested;

import static com.testingai.jooq.generated.Tables.CATEGORY;
import static com.testingai.jooq.generated.Tables.CUSTOMER;
import static com.testingai.jooq.generated.Tables.ORDERS;
import static com.testingai.jooq.generated.Tables.ORDER_ITEM;
import static com.testingai.jooq.generated.Tables.PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jooq.JooqTest;
import org.springframework.context.annotation.Import;

@JooqTest
@Import(OrderQueryService.class)
class OrderQueryServiceTest {

	@Autowired
	private DSLContext ctx;

	@Autowired
	private OrderQueryService orderQueryService;

	@Test
	void fetchesAnOrderWithNestedItemsInOneQuery() {
		Long categoryId = ctx.insertInto(CATEGORY, CATEGORY.NAME).values("Books").returning(CATEGORY.ID).fetchOne()
				.getId();
		Long customerId = ctx.insertInto(CUSTOMER, CUSTOMER.NAME, CUSTOMER.EMAIL)
				.values("Ada Lovelace", "ada@example.com").returning(CUSTOMER.ID).fetchOne().getId();
		Long productId = ctx.insertInto(PRODUCT, PRODUCT.CATEGORY_ID, PRODUCT.NAME, PRODUCT.PRICE, PRODUCT.STOCK)
				.values(categoryId, "Refactoring", new BigDecimal("40.00"), 5).returning(PRODUCT.ID).fetchOne().getId();
		LocalDateTime placedAt = LocalDateTime.now().withNano(0);
		Long orderId = ctx.insertInto(ORDERS, ORDERS.CUSTOMER_ID, ORDERS.PLACED_AT).values(customerId, placedAt)
				.returning(ORDERS.ID).fetchOne().getId();
		ctx.insertInto(ORDER_ITEM, ORDER_ITEM.ORDER_ID, ORDER_ITEM.PRODUCT_ID, ORDER_ITEM.QUANTITY,
				ORDER_ITEM.UNIT_PRICE).values(orderId, productId, 2, new BigDecimal("40.00")).execute();

		var found = orderQueryService.findOrderWithItems(orderId);

		assertThat(found).isPresent();
		assertThat(found.get().customerId()).isEqualTo(customerId);
		assertThat(found.get().items()).hasSize(1);
		assertThat(found.get().items().get(0).productName()).isEqualTo("Refactoring");
		assertThat(found.get().items().get(0).quantity()).isEqualTo(2);
	}

	@Test
	void returnsEmptyWhenOrderDoesNotExist() {
		assertThat(orderQueryService.findOrderWithItems(999_999L)).isEmpty();
	}
}
