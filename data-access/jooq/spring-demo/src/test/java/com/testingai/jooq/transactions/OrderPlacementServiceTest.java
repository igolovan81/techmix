package com.testingai.jooq.transactions;

import static com.testingai.jooq.generated.Tables.CATEGORY;
import static com.testingai.jooq.generated.Tables.CUSTOMER;
import static com.testingai.jooq.generated.Tables.ORDERS;
import static com.testingai.jooq.generated.Tables.PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jooq.JooqTest;
import org.springframework.context.annotation.Import;

@JooqTest
@Import(OrderPlacementService.class)
class OrderPlacementServiceTest {

	@Autowired
	private DSLContext ctx;

	@Autowired
	private OrderPlacementService orderPlacementService;

	private Long categoryId;
	private Long customerId;
	private Long productId;

	@BeforeEach
	void seedCatalog() {
		categoryId = ctx.insertInto(CATEGORY, CATEGORY.NAME).values("Books").returning(CATEGORY.ID).fetchOne().getId();
		customerId = ctx.insertInto(CUSTOMER, CUSTOMER.NAME, CUSTOMER.EMAIL).values("Ada Lovelace", "ada@example.com")
				.returning(CUSTOMER.ID).fetchOne().getId();
		productId = ctx.insertInto(PRODUCT, PRODUCT.CATEGORY_ID, PRODUCT.NAME, PRODUCT.PRICE, PRODUCT.STOCK)
				.values(categoryId, "Refactoring", new BigDecimal("49.99"), 10).returning(PRODUCT.ID).fetchOne()
				.getId();
	}

	// FailureSimulator.maybeThrow fires on ~5% of calls regardless of request validity, so retry a bounded
	// number of times past simulated failures to exercise the real placeOrder outcome deterministically.
	private OrderPlaced placeOrderIgnoringSimulatedFailures(PlaceOrderRequest request) {
		for (int attempt = 0; attempt < 25; attempt++) {
			try {
				return orderPlacementService.placeOrder(request);
			} catch (RuntimeException e) {
				if (e.getMessage() != null && e.getMessage().startsWith("Simulated")) {
					continue;
				}
				throw e;
			}
		}
		throw new IllegalStateException(
				"Simulated failure hit 25 times in a row — check FailureSimulator.FAILURE_RATE");
	}

	@Test
	void placesAnOrderAndDecrementsStock() {
		OrderPlaced placed = placeOrderIgnoringSimulatedFailures(
				new PlaceOrderRequest(customerId, List.of(new OrderLineRequest(productId, 3))));

		assertThat(placed.orderId()).isNotNull();
		assertThat(placed.customerId()).isEqualTo(customerId);

		Integer remainingStock = ctx.selectFrom(PRODUCT).where(PRODUCT.ID.eq(productId)).fetchOne().getStock();
		assertThat(remainingStock).isEqualTo(7);
	}

	@Test
	void rollsBackTheWholeOrderWhenALaterLineHasInsufficientStock() {
		Long scarceProductId = ctx.insertInto(PRODUCT, PRODUCT.CATEGORY_ID, PRODUCT.NAME, PRODUCT.PRICE, PRODUCT.STOCK)
				.values(categoryId, "Limited Edition Print", new BigDecimal("199.00"), 1).returning(PRODUCT.ID)
				.fetchOne().getId();

		PlaceOrderRequest request = new PlaceOrderRequest(customerId,
				List.of(new OrderLineRequest(productId, 2), new OrderLineRequest(scarceProductId, 5)));

		assertThatThrownBy(() -> placeOrderIgnoringSimulatedFailures(request))
				.isInstanceOf(InsufficientStockException.class);

		assertThat(ctx.selectFrom(PRODUCT).where(PRODUCT.ID.eq(productId)).fetchOne().getStock()).isEqualTo(10);
		assertThat(ctx.selectFrom(PRODUCT).where(PRODUCT.ID.eq(scarceProductId)).fetchOne().getStock()).isEqualTo(1);
		assertThat(ctx.fetchCount(ORDERS)).isZero();
	}
}
