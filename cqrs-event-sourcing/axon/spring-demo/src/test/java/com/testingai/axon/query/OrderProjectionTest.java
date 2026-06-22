package com.testingai.axon.query;

import com.testingai.axon.event.OrderCancelledEvent;
import com.testingai.axon.event.OrderConfirmedEvent;
import com.testingai.axon.event.OrderCreatedEvent;
import com.testingai.axon.event.OrderLineAddedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderProjectionTest {

	private static final String ORDER_ID = "order-1";
	private static final String CUSTOMER_ID = "customer-1";

	private final OrderProjection projection = new OrderProjection();

	@Test
	void onOrderCreated_shouldAddSummaryWithCreatedStatus() {
		projection.on(new OrderCreatedEvent(ORDER_ID, CUSTOMER_ID));

		OrderSummary summary = projection.handle(new FindOrderQuery(ORDER_ID));

		assertThat(summary).isEqualTo(new OrderSummary(ORDER_ID, CUSTOMER_ID, 0, "CREATED"));
	}

	@Test
	void onOrderLineAdded_shouldIncrementLineCount() {
		projection.on(new OrderCreatedEvent(ORDER_ID, CUSTOMER_ID));
		projection.on(new OrderLineAddedEvent(ORDER_ID, "product-1", 2, BigDecimal.TEN));
		projection.on(new OrderLineAddedEvent(ORDER_ID, "product-2", 1, BigDecimal.ONE));

		OrderSummary summary = projection.handle(new FindOrderQuery(ORDER_ID));

		assertThat(summary.lineCount()).isEqualTo(2);
	}

	@Test
	void onOrderConfirmed_shouldUpdateStatusToConfirmed() {
		projection.on(new OrderCreatedEvent(ORDER_ID, CUSTOMER_ID));
		projection.on(new OrderConfirmedEvent(ORDER_ID));

		OrderSummary summary = projection.handle(new FindOrderQuery(ORDER_ID));

		assertThat(summary.status()).isEqualTo("CONFIRMED");
	}

	@Test
	void onOrderCancelled_shouldUpdateStatusToCancelled() {
		projection.on(new OrderCreatedEvent(ORDER_ID, CUSTOMER_ID));
		projection.on(new OrderCancelledEvent(ORDER_ID));

		OrderSummary summary = projection.handle(new FindOrderQuery(ORDER_ID));

		assertThat(summary.status()).isEqualTo("CANCELLED");
	}

	@Test
	void handleFindOrderQuery_shouldReturnNullWhenOrderUnknown() {
		assertThat(projection.handle(new FindOrderQuery("missing"))).isNull();
	}

	@Test
	void handleFindAllOrdersQuery_shouldReturnAllKnownOrders() {
		projection.on(new OrderCreatedEvent("order-1", CUSTOMER_ID));
		projection.on(new OrderCreatedEvent("order-2", CUSTOMER_ID));

		List<OrderSummary> summaries = projection.handle(new FindAllOrdersQuery());

		assertThat(summaries).hasSize(2).extracting(OrderSummary::orderId).containsExactlyInAnyOrder("order-1",
				"order-2");
	}

	@Test
	void onReset_shouldClearAllSummaries() {
		projection.on(new OrderCreatedEvent(ORDER_ID, CUSTOMER_ID));

		projection.onReset();

		assertThat(projection.handle(new FindAllOrdersQuery())).isEmpty();
	}
}
