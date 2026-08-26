package com.testingai.disruptor.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEventTest {

	@Test
	void setCopiesAllFieldsFromTheOrderAndStampsAPublishTimestamp() {
		OrderEvent event = new OrderEvent();
		Order order = new Order("order-1", "AAPL", Side.BUY, 10, BigDecimal.TEN);

		long before = System.nanoTime();
		event.set(order);
		long after = System.nanoTime();

		assertThat(event.getOrderId()).isEqualTo("order-1");
		assertThat(event.getSymbol()).isEqualTo("AAPL");
		assertThat(event.getSide()).isEqualTo(Side.BUY);
		assertThat(event.getQuantity()).isEqualTo(10);
		assertThat(event.getPrice()).isEqualByComparingTo(BigDecimal.TEN);
		assertThat(event.getPublishNanos()).isBetween(before, after);
	}
}
